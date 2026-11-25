import { useEffect, useMemo, useState } from 'react'
import './App.css'
import { Topbar } from './components/Topbar'
import {
  PAGE_DATA,
  ROLES,
  SEARCH_INDEX,
  canAccess,
  formatRupees,
  getFirstPage,
  getGroupForPage,
} from './data'
import { Dialog } from './components/Dialog'
import { PosPanel } from './components/PosPanel'
import { Sidebar } from './components/Sidebar'
import { normalizePhone, readPosRecords } from './posStorage'
import {
  readReceivedDevices,
  recordStockRefund,
  recordStockSale,
  saveStockReceipt,
} from './stockStorage'
import { readSuppliers, saveSupplier } from './supplierStorage'
import { readAccountingEntries, saveAccountingEntries, saveAccountingEntry } from './accountingStorage'
import { readRoleDefinitions, saveRoleDefinitions } from './roleStorage'
import { readHrData, saveCommissionRates, saveEmployee } from './hrStorage'
import {
  AccountingEntryForm,
  AccountingView,
  DashboardView,
  HRPayrollView,
  ImeiView,
  ListView,
  PlaceholderView,
  ReceiveView,
  ReportsView,
  RoleManagerView,
  SupplierForm,
  TillView,
} from './components/Views'

const BRANCHES = ['Colombo 03', 'Kandy', 'Galle']
const PAGE_LABELS = {
  pos: 'Till',
  counts: 'Stock counts',
  accounting: 'Accounting',
  vat: 'VAT',
  hr: 'HR and payroll',
  users: 'Users and roles',
  settings: 'Settings',
}

function getSalePaymentAccount(paymentMethods = []) {
  if (paymentMethods.length !== 1) return 'Payment clearing'
  return {
    Cash: 'Cash',
    Card: 'Card clearing',
    Wallet: 'Wallet clearing',
    Installment: 'Accounts receivable',
  }[paymentMethods[0]] || 'Payment clearing'
}

function getSalePaymentAllocations(sale) {
  if (sale.paymentAllocations?.length) return sale.paymentAllocations
  const paymentMethods = sale.paymentMethods || ['Cash']
  return paymentMethods.length === 1
    ? [{ method: paymentMethods[0], amount: sale.total }]
    : [{ method: 'Payment clearing', amount: sale.total }]
}

function getRefundAllocations(sale, amount) {
  const originalAllocations = sale ? getSalePaymentAllocations(sale) : []
  const totalPaid = originalAllocations.reduce((sum, allocation) => sum + allocation.amount, 0)
  if (!totalPaid) return [{ method: 'Cash', amount }]

  let remainder = Math.round(amount * 100) / 100
  return originalAllocations.flatMap((allocation, index) => {
    const allocationAmount = index === originalAllocations.length - 1
      ? remainder
      : Math.round((amount * allocation.amount / totalPaid) * 100) / 100
    remainder = Math.round((remainder - allocationAmount) * 100) / 100
    return allocationAmount > 0
      ? [{ method: allocation.method, amount: allocationAmount }]
      : []
  })
}

function canOpenPage(role, page, roleDefinitions) {
  if (page === 'users') return role === 'Owner'
  if (page === 'home' || page === 'reports') return canAccess(role, '', page, roleDefinitions)
  const groupId = getGroupForPage(page)
  return Boolean(groupId) && canAccess(role, groupId, page, roleDefinitions)
}

function createSaleAccountingEntries(sale, branch, stockMovement) {
  const items = sale.items || []
  const quantity = items.reduce((total, item) => total + item.quantity, 0)
  const itemSummary = items.map((item) =>
    `${item.quantity} × ${item.name}${item.imei ? ` (IMEI ${item.imei})` : ''}`,
  ).join(', ')
  const trackingSummary = stockMovement
    ? `Inventory matched ${stockMovement.trackedQuantity} of ${quantity} units.`
    : 'Inventory quantity update could not be saved.'
  const paymentAllocations = getSalePaymentAllocations(sale)
  const entries = paymentAllocations.map((allocation, index) => ({
    sourceId: index === 0
      ? `sale:${sale.invoice}`
      : `sale:${sale.invoice}:payment:${allocation.method}`,
    source: 'Sale',
    date: sale.createdAt.slice(0, 10),
    reference: sale.invoice,
    description: `Sale to ${sale.customer?.name || 'customer'}: ${itemSummary}. Tender: ${allocation.method}. ${index === 0 ? trackingSummary : ''}`,
    quantity: index === 0 ? quantity : 0,
    branch: sale.branch || branch,
    debitAccount: getSalePaymentAccount([allocation.method]),
    creditAccount: 'Sales revenue',
    amount: allocation.amount,
  }))

  if (stockMovement?.costOfGoodsSold > 0) {
    entries.push({
      sourceId: `sale-cogs:${sale.invoice}`,
      source: 'Inventory reduction',
      date: sale.createdAt.slice(0, 10),
      reference: sale.invoice,
      description: `Cost of ${stockMovement.costedQuantity} tracked units sold.`,
      quantity: stockMovement.trackedQuantity,
      branch: sale.branch || branch,
      debitAccount: 'Cost of goods sold',
      creditAccount: 'Inventory',
      amount: stockMovement.costOfGoodsSold,
    })
  }

  if (sale.commissionTotal > 0 && sale.employeeId) {
    entries.push({
      sourceId: `commission:${sale.invoice}`,
      source: 'Commission accrued',
      date: sale.createdAt.slice(0, 10),
      reference: sale.invoice,
      description: `${sale.employeeName} earned commission on sale ${sale.invoice}.`,
      quantity,
      branch: sale.branch || branch,
      employeeId: sale.employeeId,
      debitAccount: 'Commission expense',
      creditAccount: 'Commissions payable',
      amount: sale.commissionTotal,
    })
  }

  return entries
}

function createReceiptAccountingEntry(receipt, addedDevices, receiptId = addedDevices[0]?.receiptId) {
  const firstDevice = addedDevices[0]
  const imeis = addedDevices.map((device) => device.imei).join(', ')
  return {
    sourceId: `receipt:${receiptId}`,
    source: 'Stock received',
    date: firstDevice.receivedAt.slice(0, 10),
    reference: receipt.purchaseOrder,
    description: `Received ${receipt.quantity} × ${receipt.category || 'device'} ${receipt.productName || ''} (${receipt.productCode}) from ${receipt.supplier}. IMEIs: ${imeis}`,
    quantity: receipt.quantity || addedDevices.length,
    branch: receipt.branch,
    debitAccount: 'Inventory',
    creditAccount: 'Accounts payable',
    amount: receipt.totalBuyingPrice,
  }
}

function App() {
  const [initialRoleData] = useState(() => {
    try {
      return { roles: readRoleDefinitions(), loadError: '' }
    } catch (error) {
      return { roles: { ...ROLES }, loadError: error.message }
    }
  })
  const [roles, setRoles] = useState(initialRoleData.roles)
  const [role, setRole] = useState('Owner')
  const [page, setPage] = useState('home')
  const [openGroup, setOpenGroup] = useState('')
  const [branch, setBranch] = useState(BRANCHES[0])
  const [menuOpen, setMenuOpen] = useState(false)
  const [posOpen, setPosOpen] = useState(false)
  const [search, setSearch] = useState('')
  const [selectedImei, setSelectedImei] = useState('')
  const [dialog, setDialog] = useState(null)
  const [supplierFormOpen, setSupplierFormOpen] = useState(false)
  const [accountingFormOpen, setAccountingFormOpen] = useState(false)
  const [discount, setDiscount] = useState(0)
  const [initialPosData] = useState(() => {
    try {
      const records = readPosRecords()
      return { customers: records.customers, sales: records.sales, loadError: '' }
    } catch (error) {
      return { customers: [], sales: [], loadError: error.message }
    }
  })
  const [savedCustomers, setSavedCustomers] = useState(initialPosData.customers)
  const [savedSales, setSavedSales] = useState(initialPosData.sales)
  const [initialStockData] = useState(() => {
    try {
      return { devices: readReceivedDevices(), loadError: '' }
    } catch (error) {
      return { devices: [], loadError: error.message }
    }
  })
  const [receivedDevices, setReceivedDevices] = useState(initialStockData.devices)
  const [initialSupplierData] = useState(() => {
    try {
      return { suppliers: readSuppliers(), loadError: '' }
    } catch (error) {
      return { suppliers: [], loadError: error.message }
    }
  })
  const [supplierRecords, setSupplierRecords] = useState(initialSupplierData.suppliers)
  const [initialAccountingData] = useState(() => {
    try {
      return { entries: readAccountingEntries(), loadError: '' }
    } catch (error) {
      return { entries: [], loadError: error.message }
    }
  })
  const [accountingEntries, setAccountingEntries] = useState(initialAccountingData.entries)
  const [initialHrData] = useState(() => {
    try {
      return { data: readHrData(), loadError: '' }
    } catch (error) {
      return {
        data: { employees: [], commissionRates: { Phones: 0, Laptops: 0, Accessories: 0 } },
        loadError: error.message,
      }
    }
  })
  const [employees, setEmployees] = useState(initialHrData.data.employees)
  const [commissionRates, setCommissionRates] = useState(initialHrData.data.commissionRates)
  const initialLoadError = [
    initialRoleData.loadError,
    initialPosData.loadError,
    initialStockData.loadError,
    initialSupplierData.loadError,
    initialAccountingData.loadError,
    initialHrData.loadError,
  ].filter(Boolean).join(' ')
  const [toast, setToast] = useState(initialLoadError
    ? `Saved records could not be loaded: ${initialLoadError}`
    : '')

  const searchResults = useMemo(() => {
    const query = search.trim().toLowerCase()
    if (!query) return []
    return SEARCH_INDEX.filter((item) =>
      item.some((value) => value.toLowerCase().includes(query)),
    )
  }, [search])

  useEffect(() => {
    if (!toast) return undefined
    const timer = window.setTimeout(() => setToast(''), 2400)
    return () => window.clearTimeout(timer)
  }, [toast])

  useEffect(() => {
    const handleKeys = (event) => {
      if (event.key === 'Escape') {
        if (supplierFormOpen) setSupplierFormOpen(false)
        else if (accountingFormOpen) setAccountingFormOpen(false)
        else if (dialog) setDialog(null)
        else if (posOpen) setPosOpen(false)
        else setMenuOpen(false)
      }
      if (
        (!dialog && (event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') ||
        (event.key === '/' && !/INPUT|TEXTAREA|SELECT/.test(document.activeElement.tagName))
      ) {
        event.preventDefault()
        document.querySelector(posOpen ? '#pos-search' : '#global-search')?.focus()
      }
    }
    document.addEventListener('keydown', handleKeys)
    return () => document.removeEventListener('keydown', handleKeys)
  }, [accountingFormOpen, dialog, posOpen, supplierFormOpen])

  const notify = (message) => setToast(message)

  useEffect(() => {
    const timer = window.setTimeout(() => {
      const knownSourceIds = new Set(accountingEntries.map((entry) => entry.sourceId).filter(Boolean))
      const pendingEntries = []
      let updatedDevices = receivedDevices
      let migrationError = ''

      for (const sale of savedSales) {
        const saleSourceId = `sale:${sale.invoice}`
        const cogsSourceId = `sale-cogs:${sale.invoice}`
        if (knownSourceIds.has(saleSourceId) && knownSourceIds.has(cogsSourceId)) continue
        let stockMovement
        try {
          stockMovement = recordStockSale(sale, sale.branch || branch)
          if (stockMovement.updatedQuantity) updatedDevices = stockMovement.devices
        } catch (error) {
          migrationError = `Historical sale ${sale.invoice} inventory update failed: ${error.message}`
        }
        pendingEntries.push(...createSaleAccountingEntries(sale, sale.branch || branch, stockMovement)
          .filter((entry) => !knownSourceIds.has(entry.sourceId)))
      }

      const receiptGroups = new Map()
      for (const device of receivedDevices) {
        const groupId = device.receiptId ||
          `${device.purchaseOrder}:${device.receivedAt}:${device.productCode}:${device.branch}`
        const group = receiptGroups.get(groupId) || []
        group.push(device)
        receiptGroups.set(groupId, group)
      }
      for (const [groupId, devices] of receiptGroups) {
        if (knownSourceIds.has(`receipt:${groupId}`)) continue
        const firstDevice = devices[0]
        if (!Number.isFinite(firstDevice.totalBuyingPrice) || firstDevice.totalBuyingPrice <= 0) {
          migrationError = `Received stock for ${firstDevice.purchaseOrder} has no buying total to audit`
          continue
        }
        pendingEntries.push(createReceiptAccountingEntry({
          ...firstDevice,
          quantity: firstDevice.quantity || devices.length,
        }, devices, groupId))
      }

      if (!pendingEntries.length) {
        if (updatedDevices !== receivedDevices) setReceivedDevices(updatedDevices)
        if (migrationError) setToast(migrationError)
        return
      }
      try {
        const entries = saveAccountingEntries(pendingEntries)
        if (entries.length) setAccountingEntries((current) => [...entries, ...current])
        if (updatedDevices !== receivedDevices) setReceivedDevices(updatedDevices)
        if (migrationError) setToast(migrationError)
      } catch (error) {
        setToast(`Existing sales or receipts could not be audited: ${error.message}`)
      }
    }, 0)
    return () => window.clearTimeout(timer)
  }, [accountingEntries, branch, receivedDevices, savedSales])

  const navigate = (nextPage) => {
    if (!canOpenPage(role, nextPage, roles)) {
      notify('Your current role does not have access to that section')
      return
    }
    setMenuOpen(false)
    if (nextPage === 'pos') {
      setPosOpen(true)
      return
    }
    setPage(nextPage)
    setOpenGroup(getGroupForPage(nextPage))
  }

  const changeRole = (nextRole) => {
    if (!roles[nextRole]) return
    const firstPage = getFirstPage(nextRole, roles)
    setRole(nextRole)
    setOpenGroup(getGroupForPage(firstPage))
    setPage(firstPage)
    setMenuOpen(false)
    setPosOpen(firstPage === 'pos')
  }

  const selectSearchResult = (imei) => {
    if (!canOpenPage(role, 'imei', roles)) {
      notify('Your current role does not have access to stock records')
      return
    }
    setSelectedImei(imei)
    setSearch('')
    setPage('imei')
    setOpenGroup('stock')
    setMenuOpen(false)
  }

  const handleSaveRole = (name, permissions) => {
    try {
      const savedRoles = saveRoleDefinitions({ ...roles, [name]: permissions })
      setRoles(savedRoles)
      notify(`Role "${name}" saved`)
      return true
    } catch (error) {
      notify(`Role could not be saved: ${error.message}`)
      return false
    }
  }

  const handleDeleteRole = (name) => {
    const updatedRoles = { ...roles }
    delete updatedRoles[name]
    try {
      setRoles(saveRoleDefinitions(updatedRoles))
      notify(`Role "${name}" deleted`)
    } catch (error) {
      notify(`Role could not be deleted: ${error.message}`)
    }
  }

  const openRefundDialog = (reference) => {
    const sale = savedSales.find((record) => record.invoice === reference)
    const staticInvoice = PAGE_DATA.invoices.rows.find((row) => row[0] === reference)
    if (!sale && staticInvoice?.[5] !== 'Paid') {
      notify('This invoice has no recorded paid balance available to refund')
      return
    }
    const total = sale?.total ?? Number((staticInvoice?.[4] || '').replace(/[^\d.]/g, ''))
    const alreadyRefunded = accountingEntries
      .filter((entry) => entry.source === 'Refund' && entry.reference === reference)
      .reduce((sum, entry) => sum + entry.amount, 0)
    const remaining = Math.max(0, total - alreadyRefunded)
    if (!remaining) {
      notify('There is no refundable balance remaining on this invoice')
      return
    }
    setDialog({
      kind: 'refund',
      reference,
      sale,
      remaining,
      amount: '',
      refundId: `refund:${reference}:${Date.now()}`,
      returnItems: sale?.items.map((item) => ({
        ...item,
        returnedQuantity: 0,
      })) || [],
    })
  }

  const confirmDialog = () => {
    if (dialog?.kind === 'refund') {
      const amount = Number(dialog.amount)
      if (!Number.isFinite(amount) || amount <= 0 || amount > dialog.remaining) {
        notify(`Enter a refund amount up to ${formatRupees(dialog.remaining)}`)
        return false
      }
      const today = new Date()
      const localDate = new Date(today.getTime() - today.getTimezoneOffset() * 60000)
        .toISOString()
        .slice(0, 10)
      const returnItems = (dialog.returnItems || [])
        .filter((item) => Number(item.returnedQuantity) > 0)
        .map((item) => ({ ...item, returnedQuantity: Number(item.returnedQuantity) }))
      let stockReturn
      if (returnItems.length) {
        try {
          stockReturn = recordStockRefund(
            dialog.sale,
            returnItems,
            dialog.sale.branch || branch,
            dialog.refundId,
          )
          setReceivedDevices(stockReturn.devices)
        } catch (error) {
          notify(`Refund was not recorded: ${error.message}`)
          return false
        }
      }

      const refundAllocations = getRefundAllocations(dialog.sale, amount)
      const entries = refundAllocations.map((allocation, index) => ({
        sourceId: index === 0
          ? dialog.refundId
          : `${dialog.refundId}:${allocation.method}`,
        source: 'Refund',
        date: localDate,
        reference: dialog.reference,
        description: `Refund via ${allocation.method}${index === 0 ? `: ${formatRupees(amount)}${dialog.sale?.customer?.name ? ` to ${dialog.sale.customer.name}` : ''}. Returned to stock: ${stockReturn?.returnedQuantity || 0} unit(s).` : '.'}`,
        quantity: index === 0 ? stockReturn?.returnedQuantity || 0 : 0,
        branch: dialog.sale?.branch || branch,
        debitAccount: 'Sales returns',
        creditAccount: getSalePaymentAccount([allocation.method]),
        amount: allocation.amount,
      }))
      if (stockReturn?.costOfGoodsReturned > 0) {
        entries.push({
          sourceId: `stock-return:${dialog.refundId}`,
          source: 'Inventory returned',
          date: localDate,
          reference: dialog.reference,
          description: `Cost of ${stockReturn.returnedQuantity} returned unit(s) restored to inventory.`,
          quantity: stockReturn.returnedQuantity,
          branch: dialog.sale?.branch || branch,
          debitAccount: 'Inventory',
          creditAccount: 'Cost of goods sold',
          amount: stockReturn.costOfGoodsReturned,
        })
      }
      if (dialog.sale?.commissionTotal > 0 && dialog.sale.employeeId && dialog.sale.total > 0) {
        const previouslyReversed = accountingEntries
          .filter((entry) => entry.source === 'Commission reversal' && entry.reference === dialog.reference)
          .reduce((sum, entry) => sum + entry.amount, 0)
        const commissionRemaining = Math.max(0, dialog.sale.commissionTotal - previouslyReversed)
        const commissionReversal = amount >= dialog.remaining
          ? commissionRemaining
          : Math.min(
            commissionRemaining,
            Math.round(dialog.sale.commissionTotal * amount / dialog.sale.total * 100) / 100,
          )
        if (commissionReversal > 0) {
          entries.push({
            sourceId: `${dialog.refundId}:commission`,
            source: 'Commission reversal',
            date: localDate,
            reference: dialog.reference,
            description: `Commission reversed proportionally for ${dialog.sale.employeeName} after refund.`,
            quantity: 0,
            branch: dialog.sale.branch || branch,
            employeeId: dialog.sale.employeeId,
            debitAccount: 'Commissions payable',
            creditAccount: 'Commission expense',
            amount: commissionReversal,
          })
        }
      }
      try {
        const savedEntries = saveAccountingEntries(entries)
        if (savedEntries.length) setAccountingEntries((current) => [...savedEntries, ...current])
      } catch (error) {
        notify(`Refund was not recorded in accounting: ${error.message}`)
        return false
      }
      notify(`Refund of ${formatRupees(amount)} recorded for ${dialog.reference}`)
      return true
    }
    if (dialog?.kind === 'discount') {
      if (dialog.pin !== '1234') {
        notify('Wrong PIN')
        return false
      }
      setDiscount(dialog.amount)
      notify('Discount approved')
      return true
    }
    return true
  }

  const renderPage = () => {
    if (page === 'home') {
      return (
        <DashboardView
          role={role}
          branch={branch}
          onNavigate={navigate}
          canAccess={(selectedRole, groupId, itemId) =>
            canAccess(selectedRole, groupId, itemId, roles)}
        />
      )
    }
    if (page === 'reports') return <ReportsView branch={branch} />
    if (page === 'pos') return <TillView onOpen={() => setPosOpen(true)} />
    if (page === 'users' && role === 'Owner') {
      return (
        <RoleManagerView
          roles={roles}
          builtInRoles={Object.keys(ROLES)}
          onSave={handleSaveRole}
          onDelete={handleDeleteRole}
        />
      )
    }
    if (page === 'hr') {
      return (
        <HRPayrollView
          employees={employees}
          commissionRates={commissionRates}
          sales={savedSales}
          accountingEntries={accountingEntries}
          onAddEmployee={(name, employeeCode) => {
            try {
              const data = saveEmployee(name, employeeCode)
              setEmployees(data.employees)
              notify('Employee saved')
              return true
            } catch (error) {
              notify(`Employee could not be saved: ${error.message}`)
              return false
            }
          }}
          onSaveCommissionRates={(rates) => {
            try {
              const data = saveCommissionRates(rates)
              setCommissionRates(data.commissionRates)
              notify('Commission rates saved')
              return true
            } catch (error) {
              notify(`Commission rates could not be saved: ${error.message}`)
              return false
            }
          }}
        />
      )
    }
    if (page === 'receive') {
      return <ReceiveView branch={branch} onComplete={(receipt) => {
        let addedDevices
        try {
          addedDevices = saveStockReceipt(receipt)
        } catch (error) {
          notify(`Stock could not be received: ${error.message}`)
          return
        }
        setReceivedDevices((current) => [...current, ...addedDevices])
        try {
          const [entry] = saveAccountingEntries([createReceiptAccountingEntry(receipt, addedDevices)])
          if (entry) setAccountingEntries((current) => [entry, ...current])
        } catch (error) {
          notify(`Stock received, but its accounting entry could not be saved: ${error.message}`)
          navigate('inventory')
          return
        }
        notify(`${addedDevices.length} device${addedDevices.length === 1 ? '' : 's'} received and recorded in accounting`)
        navigate('inventory')
      }} onNotify={notify} />
    }
    if (page === 'accounting') {
      return (
        <AccountingView
          branch={branch}
          entries={accountingEntries}
          onNew={() => setAccountingFormOpen(true)}
        />
      )
    }
    if (page === 'imei') {
      return (
        <ImeiView
          key={selectedImei}
          initialImei={selectedImei}
          onSearch={(value) => setSelectedImei(value)}
        />
      )
    }
    if (PAGE_DATA[page]) {
      return (
        <ListView
          key={page}
          page={page}
          branch={branch}
          customerRecords={page === 'customers' ? savedCustomers : undefined}
          sales={page === 'invoices' ? savedSales : undefined}
          receivedDevices={page === 'inventory' ? receivedDevices : undefined}
          supplierRecords={page === 'suppliers' ? supplierRecords : undefined}
          onNew={() => {
            if (page === 'purchases') navigate('receive')
            else if (page === 'suppliers') setSupplierFormOpen(true)
            else notify(`A short “${PAGE_DATA[page].action.toLowerCase()}” form would open here`)
          }}
          onRefund={openRefundDialog}
        />
      )
    }
    const label = PAGE_LABELS[page] || page
    return (
      <PlaceholderView
        title={label}
        branch={branch}
        onAction={() => setDialog({
          kind: 'info',
          title: 'New accounting entry',
          message: 'Accounting entry creation is not connected to a data service in this project, so no entry can be saved here yet.',
        })}
      />
    )
  }

  return (
    <div className="app-shell">
      <Sidebar
        role={role}
        roles={roles}
        page={page}
        openGroup={openGroup}
        menuOpen={menuOpen}
        onToggleMenu={() => setMenuOpen((current) => !current)}
        onToggleGroup={(groupId) => setOpenGroup((current) => current === groupId ? '' : groupId)}
        onNavigate={navigate}
        onRoleChange={changeRole}
      />
      <div className="main">
        <Topbar
          branch={branch}
          onBranchChange={(nextBranch) => {
            setBranch(nextBranch)
            notify(`Switched to ${nextBranch}`)
          }}
          role={role}
          search={search}
          searchResults={searchResults}
          onSearchChange={setSearch}
          onSearchSelect={selectSearchResult}
          onMenuToggle={() => setMenuOpen((current) => !current)}
          onNotify={() => notify("You're all caught up")}
        />
        <main className="page-content">{renderPage()}</main>
      </div>
      <PosPanel
        isOpen={posOpen}
        branch={branch}
        employees={employees}
        commissionRates={commissionRates}
        discount={discount}
        onDiscountChange={setDiscount}
        onRequestDiscount={(amount) => setDialog({ kind: 'discount', amount, pin: '' })}
        onNotify={notify}
        onSaleComplete={(sale) => {
          let stockMovement
          const auditIssues = []
          try {
            stockMovement = recordStockSale(sale.sale, branch)
            setReceivedDevices(stockMovement.devices)
          } catch (error) {
            auditIssues.push(`inventory quantity update failed: ${error.message}`)
          }
          setSavedCustomers((current) => {
            const phoneKey = normalizePhone(sale.customer.phone)
            const existingIndex = current.findIndex((item) => normalizePhone(item.phone) === phoneKey)
            return existingIndex < 0
              ? [...current, sale.customer]
              : current.map((item, index) => index === existingIndex ? sale.customer : item)
          })
          setSavedSales((current) => [...current, sale.sale])
          try {
            const entries = saveAccountingEntries(
              createSaleAccountingEntries(sale.sale, branch, stockMovement),
            )
            if (entries.length) setAccountingEntries((current) => [...entries, ...current])
          } catch (error) {
            auditIssues.push(`accounting entry failed: ${error.message}`)
          }
          if (stockMovement?.untrackedQuantity) {
            auditIssues.push(`${stockMovement.untrackedQuantity} sold unit(s) had no received-stock match`)
          }
          if (
            stockMovement &&
            stockMovement.costedQuantity < stockMovement.trackedQuantity
          ) {
            auditIssues.push('purchase cost was missing for some tracked units, so COGS is incomplete')
          }
          return auditIssues.join('; ')
        }}
        onClose={() => setPosOpen(false)}
      />
      <Dialog
        dialog={dialog}
        onChange={(nextDialog) => setDialog(nextDialog)}
        onClose={() => setDialog(null)}
        onAction={confirmDialog}
      />
      {supplierFormOpen && (
        <SupplierForm
          onClose={() => setSupplierFormOpen(false)}
          onSave={(details) => {
            try {
              const supplier = saveSupplier(details)
              setSupplierRecords((current) => [...current, supplier])
              setSupplierFormOpen(false)
              notify('Supplier saved')
            } catch (error) {
              notify(`Supplier could not be saved: ${error.message}`)
            }
          }}
        />
      )}
      {accountingFormOpen && (
        <AccountingEntryForm
          branch={branch}
          onClose={() => setAccountingFormOpen(false)}
          onSave={(details) => {
            try {
              const entry = saveAccountingEntry(details)
              setAccountingEntries((current) => [entry, ...current])
              setAccountingFormOpen(false)
              notify('Accounting entry saved')
            } catch (error) {
              notify(`Accounting entry could not be saved: ${error.message}`)
            }
          }}
        />
      )}
      <div className={`toast${toast ? ' visible' : ''}`} role="status">{toast}</div>
    </div>
  )
}

export default App
