import { useRef, useState } from 'react'
import { IMEI_DATA, NAV_GROUPS, PAGE_DATA, formatRupees, statusClass } from '../data'
import { COMMISSION_CATEGORIES } from '../hrStorage'
import { normalizePhone } from '../posStorage'
import { Button, DataTable, EmptyState, FilterBar, PageHeader } from './Ui'

const ACCOUNT_OPTIONS = [
  'Cash',
  'Bank',
  'Card clearing',
  'Wallet clearing',
  'Payment clearing',
  'Accounts receivable',
  'Inventory',
  'Accounts payable',
  'Sales revenue',
  'Sales returns',
  'Cost of goods sold',
  'Commission expense',
  'Commissions payable',
  'Rent expense',
  'Utilities expense',
  'Other',
]

export function DashboardView({ role, branch, onNavigate, canAccess: hasAccess }) {
  const cards = [
    ['Today’s sales', 'Rs 1,284,500', 'invoices'],
    ['Low stock items', '7', 'inventory'],
    ['Overdue installments', '4', 'installments'],
    ['Repairs waiting', '3', 'repairs'],
    ['Warranty claims pending', '2', 'warranty'],
  ].filter(([, , target]) =>
    NAV_GROUPS.some((group) =>
      group.items.some(([itemId]) => itemId === target && hasAccess(role, group.id, target)),
    ),
  )

  return (
    <>
      <PageHeader
        title="Home"
        breadcrumbs={['Dashboard', 'Home']}
        context={`Good morning, ${role} · ${branch} branch`}
      />
      <div className="todo">
        {cards.map(([label, amount, target]) => (
          <button type="button" key={label} onClick={() => onNavigate(target)}>
            <b>{amount}</b>
            <span>{label}</span>
          </button>
        ))}
      </div>
      <div className="card section-card">
        <div className="section-title">
          <h2>Quick actions</h2>
          <span>Common tasks</span>
        </div>
        <div className="quick-actions">
          {hasAccess(role, 'sell', 'pos') && (
            <Button type="button" icon="+" onClick={() => onNavigate('pos')}>Open the till</Button>
          )}
          {hasAccess(role, 'buy', 'receive') && (
            <Button type="button" variant="secondary" onClick={() => onNavigate('receive')}>Receive stock</Button>
          )}
          <Button type="button" variant="secondary" onClick={() => onNavigate('imei')}>Look up an IMEI</Button>
        </div>
      </div>
    </>
  )
}

export function ListView({
  page,
  branch,
  onNew,
  onRefund,
  customerRecords = [],
  sales = [],
  receivedDevices = [],
  supplierRecords = [],
}) {
  const data = PAGE_DATA[page]
  const [query, setQuery] = useState('')
  const [status, setStatus] = useState('')
  const rows = page === 'customers'
    ? [
      ...data.rows.map(([name, phone, points, customerStatus]) => {
        const savedCustomer = customerRecords.find((customer) =>
          normalizePhone(customer.phone) === normalizePhone(phone),
        )
        return [
          savedCustomer?.name || name,
          savedCustomer?.phone || phone,
          savedCustomer?.email || '—',
          savedCustomer?.address || '—',
          savedCustomer?.nic || '—',
          points,
          customerStatus,
        ]
      }),
      ...customerRecords
        .filter((customer) => !data.rows.some((row) =>
          normalizePhone(row[1]) === normalizePhone(customer.phone),
        ))
        .map((customer) => [
          customer.name,
          customer.phone,
          customer.email || '—',
          customer.address || '—',
          customer.nic || '—',
          '—',
          'Active',
        ]),
    ]
    : page === 'suppliers'
      ? [
        ...data.rows,
        ...supplierRecords.map((supplier) => [
          supplier.name,
          supplier.contactName || '—',
          supplier.phone,
          supplier.email || '—',
          supplier.address,
          supplier.taxNumber || '—',
          supplier.paymentTerms,
          'Rs 0',
          supplier.status || 'Active',
        ]),
      ]
    : page === 'invoices'
      ? [
        ...data.rows,
        ...sales.map((sale) => {
          const date = new Date(sale.createdAt)
          return [
            sale.invoice,
            sale.customer?.name || '—',
            Number.isNaN(date.getTime()) ? '—' : date.toLocaleDateString(undefined, {
              day: 'numeric',
              month: 'short',
              year: 'numeric',
            }),
            Number.isNaN(date.getTime()) ? '—' : date.toLocaleTimeString(undefined, {
              hour: '2-digit',
              minute: '2-digit',
            }),
            formatRupees(sale.total),
            sale.status || 'Paid',
            'Refund',
          ]
        }),
      ]
      : page === 'inventory'
        ? [
          ...data.rows,
          ...receivedDevices.map((device) => {
            const inStock = device.status !== 'Sold'
            return [
              `${device.category ? `${device.category} · ` : ''}${device.productName || 'Device'} (${device.productCode})`,
              device.imei,
              device.branch === 'Colombo 03' && inStock ? '1' : '0',
              device.branch === 'Kandy' && inStock ? '1' : '0',
              device.branch === 'Galle' && inStock ? '1' : '0',
              Number.isFinite(device.buyingPricePerItem) ? formatRupees(device.buyingPricePerItem) : '—',
              Number.isFinite(device.sellingPricePerItem) ? formatRupees(device.sellingPricePerItem) : '—',
              inStock ? 'In stock' : 'Sold',
            ]
          }),
        ]
      : data.rows
  const statuses = [...new Set(rows.map((row) => row[data.statusIndex]))]
  const visibleRows = rows.filter((row) =>
    (!status || row[data.statusIndex] === status) &&
    row.join(' ').toLowerCase().includes(query.toLowerCase()),
  )

  return (
    <>
      <PageHeader
        title={data.title}
        breadcrumbs={['Dashboard', data.title]}
        context={`${branch} branch`}
        action={<Button type="button" icon="+" onClick={onNew}>{data.action}</Button>}
      />
      <FilterBar>
        <label className="filter-search">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <circle cx="10.8" cy="10.8" r="6.8" />
            <path d="m16 16 5 5" />
          </svg>
          <input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder={`Search ${data.title.toLowerCase()}...`}
            aria-label={`Search ${data.title}`}
          />
        </label>
        <label className="filter-select">
          <span>Status</span>
          <select value={status} onChange={(event) => setStatus(event.target.value)} aria-label="Filter by status">
            <option value="">All statuses</option>
            {statuses.map((statusValue) => <option key={statusValue}>{statusValue}</option>)}
          </select>
        </label>
        <span className="result-count">{visibleRows.length} records</span>
      </FilterBar>
      <DataTable columns={data.columns}>
        {visibleRows.length ? visibleRows.map((row) => (
          <tr key={row[0]}>
            {row.map((value, index) => (
              <td key={`${row[0]}-${index}`}>
                {index === data.statusIndex
                  ? <span className={`chip ${statusClass(value)}`}><i />{value}</span>
                  : value === 'Refund'
                    ? <Button type="button" variant="quiet" className="small" onClick={() => onRefund(row[0])}>Refund</Button>
                    : value}
              </td>
            ))}
          </tr>
        )) : (
          <tr><td className="table-empty" colSpan={data.columns.length}>Nothing matches. Clear the search or filter.</td></tr>
        )}
      </DataTable>
      <div className="table-footer">
        <span>Showing {visibleRows.length} of {rows.length} records</span>
        <div><Button type="button" variant="secondary" disabled>Previous</Button><span className="page-number">1</span><Button type="button" variant="secondary" disabled>Next</Button></div>
      </div>
    </>
  )
}

export function SupplierForm({ onClose, onSave }) {
  const [supplier, setSupplier] = useState({
    name: '',
    contactName: '',
    phone: '',
    email: '',
    address: '',
    taxNumber: '',
    paymentTerms: 'Due on receipt',
  })
  const [error, setError] = useState('')

  const updateField = (field, value) => {
    setSupplier((current) => ({ ...current, [field]: value }))
    setError('')
  }

  const submit = (event) => {
    event.preventDefault()
    if (
      !supplier.name.trim() ||
      supplier.phone.replace(/\D/g, '').length < 7 ||
      !supplier.address.trim() ||
      (supplier.email.trim() && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(supplier.email.trim()))
    ) {
      setError('Enter a supplier name, valid phone number, address, and valid email if provided.')
      return
    }

    onSave(Object.fromEntries(
      Object.entries(supplier).map(([field, value]) => [
        field,
        field === 'email' ? value.trim().toLowerCase() : value.trim(),
      ]),
    ))
  }

  return (
    <div className="modal supplier-modal" role="presentation" onMouseDown={(event) => {
      if (event.target === event.currentTarget) onClose()
    }}>
      <form
        className="modal-box supplier-modal-box"
        role="dialog"
        aria-modal="true"
        aria-labelledby="supplier-form-title"
        noValidate
        onSubmit={submit}
      >
        <h2 id="supplier-form-title">New supplier</h2>
        <p>Add supplier contact and payment details.</p>
        {error && <p className="supplier-form-error" role="alert">{error}</p>}
        <div className="supplier-fields">
          <label>
            Supplier name <span>*</span>
            <input
              autoFocus
              value={supplier.name}
              onChange={(event) => updateField('name', event.target.value)}
              autoComplete="organization"
              aria-label="Supplier name"
            />
          </label>
          <label>
            Contact person
            <input
              value={supplier.contactName}
              onChange={(event) => updateField('contactName', event.target.value)}
              autoComplete="name"
              aria-label="Supplier contact person"
            />
          </label>
          <label>
            Phone <span>*</span>
            <input
              type="tel"
              value={supplier.phone}
              onChange={(event) => updateField('phone', event.target.value)}
              autoComplete="tel"
              aria-label="Supplier phone"
            />
          </label>
          <label>
            Email
            <input
              type="email"
              value={supplier.email}
              onChange={(event) => updateField('email', event.target.value)}
              autoComplete="email"
              aria-label="Supplier email"
            />
          </label>
          <label className="supplier-address-field">
            Address <span>*</span>
            <textarea
              value={supplier.address}
              onChange={(event) => updateField('address', event.target.value)}
              autoComplete="street-address"
              aria-label="Supplier address"
              rows="3"
            />
          </label>
          <label>
            Tax / VAT ID
            <input
              value={supplier.taxNumber}
              onChange={(event) => updateField('taxNumber', event.target.value)}
              aria-label="Supplier tax or VAT ID"
            />
          </label>
          <label>
            Payment terms
            <select
              value={supplier.paymentTerms}
              onChange={(event) => updateField('paymentTerms', event.target.value)}
              aria-label="Supplier payment terms"
            >
              <option>Due on receipt</option>
              <option>Net 7</option>
              <option>Net 15</option>
              <option>Net 30</option>
            </select>
          </label>
        </div>
        <div className="actions">
          <Button type="button" variant="secondary" onClick={onClose}>Cancel</Button>
          <Button type="submit">Save supplier</Button>
        </div>
      </form>
    </div>
  )
}

export function AccountingView({ branch, entries, onNew }) {
  const [query, setQuery] = useState('')
  const branchEntries = entries.filter((entry) => entry.branch === branch)
  const accountBalances = [...branchEntries.reduce((balances, entry) => {
    const debit = balances.get(entry.debitAccount) || { debit: 0, credit: 0 }
    debit.debit += entry.amount
    balances.set(entry.debitAccount, debit)
    const credit = balances.get(entry.creditAccount) || { debit: 0, credit: 0 }
    credit.credit += entry.amount
    balances.set(entry.creditAccount, credit)
    return balances
  }, new Map())]
    .sort(([first], [second]) => first.localeCompare(second))
  const visibleEntries = branchEntries.filter((entry) =>
    [
      entry.source,
      entry.date,
      entry.reference,
      entry.description,
      entry.branch,
      entry.debitAccount,
      entry.creditAccount,
    ].join(' ').toLowerCase().includes(query.toLowerCase()),
  )

  return (
    <>
      <PageHeader
        title="Accounting"
        breadcrumbs={['Dashboard', 'Accounting']}
        context={`Double-entry ledger · ${branch} branch`}
        action={<Button type="button" icon="+" onClick={onNew}>New Entry</Button>}
      />
      {branchEntries.length ? (
        <>
          <div className="card accounting-balances">
            <div className="section-title"><h2>Account balances</h2><span>Net ledger position</span></div>
            <DataTable columns={['Account', 'Debits', 'Credits', 'Balance']}>
              {accountBalances.map(([account, totals]) => {
                const net = totals.debit - totals.credit
                return (
                  <tr key={account}>
                    <td>{account}</td>
                    <td>{formatRupees(totals.debit)}</td>
                    <td>{formatRupees(totals.credit)}</td>
                    <td>{formatRupees(Math.abs(net))} {net < 0 ? 'Cr' : 'Dr'}</td>
                  </tr>
                )
              })}
            </DataTable>
          </div>
          <FilterBar>
            <label className="filter-search">
              <input
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder="Search accounting entries..."
                aria-label="Search accounting entries"
              />
            </label>
            <span className="result-count">{visibleEntries.length} entries</span>
          </FilterBar>
          <DataTable columns={['Date', 'Type', 'Reference', 'Description', 'Quantity', 'Branch', 'Debit account', 'Credit account', 'Amount']}>
            {visibleEntries.length ? visibleEntries.map((entry) => (
              <tr key={entry.id}>
                <td>{entry.date}</td>
                <td>{entry.source || 'Manual'}</td>
                <td>{entry.reference || '—'}</td>
                <td>{entry.description}</td>
                <td>{entry.quantity ? `${entry.quantity} units` : '—'}</td>
                <td>{entry.branch}</td>
                <td>{entry.debitAccount}</td>
                <td>{entry.creditAccount}</td>
                <td>{formatRupees(entry.amount)}</td>
              </tr>
            )) : (
              <tr><td className="table-empty" colSpan="9">Nothing matches. Clear the search and try again.</td></tr>
            )}
          </DataTable>
        </>
      ) : (
        <div className="card accounting-empty">
          <p>Each entry records equal debit and credit amounts in the ledger.</p>
          <EmptyState
            title="No accounting entries yet"
            description="Create a journal entry to record a transaction."
            actionLabel="Create New Entry"
            onAction={onNew}
          />
        </div>
      )}
    </>
  )
}

export function AccountingEntryForm({ branch, onClose, onSave }) {
  const [entry, setEntry] = useState(() => {
    const now = new Date()
    const localDate = new Date(now.getTime() - now.getTimezoneOffset() * 60000)
    return {
      date: localDate.toISOString().slice(0, 10),
      reference: '',
      description: '',
      debitAccount: 'Cash',
      creditAccount: 'Sales revenue',
      amount: '',
    }
  })
  const [error, setError] = useState('')

  const updateField = (field, value) => {
    setEntry((current) => ({ ...current, [field]: value }))
    setError('')
  }

  const submit = (event) => {
    event.preventDefault()
    const amount = Number(entry.amount)
    if (
      !entry.date ||
      !entry.description.trim() ||
      !Number.isFinite(amount) ||
      amount <= 0 ||
      entry.debitAccount === entry.creditAccount
    ) {
      setError('Enter the date, description, positive amount, and different debit and credit accounts.')
      return
    }

    onSave({
      ...entry,
      reference: entry.reference.trim(),
      description: entry.description.trim(),
      amount,
      branch,
      source: 'Manual',
      quantity: 0,
    })
  }

  return (
    <div className="modal accounting-modal" role="presentation" onMouseDown={(event) => {
      if (event.target === event.currentTarget) onClose()
    }}>
      <form
        className="modal-box accounting-modal-box"
        role="dialog"
        aria-modal="true"
        aria-labelledby="accounting-form-title"
        noValidate
        onSubmit={submit}
      >
        <h2 id="accounting-form-title">New accounting entry</h2>
        <p>Record a balanced journal entry for the {branch} branch.</p>
        {error && <p className="accounting-form-error" role="alert">{error}</p>}
        <div className="accounting-fields">
          <label>
            Date <span>*</span>
            <input
              type="date"
              value={entry.date}
              onChange={(event) => updateField('date', event.target.value)}
              aria-label="Entry date"
            />
          </label>
          <label>
            Reference
            <input
              value={entry.reference}
              onChange={(event) => updateField('reference', event.target.value)}
              aria-label="Entry reference"
              placeholder="Invoice, receipt, or other reference"
            />
          </label>
          <label className="accounting-description-field">
            Description <span>*</span>
            <textarea
              value={entry.description}
              onChange={(event) => updateField('description', event.target.value)}
              aria-label="Entry description"
              rows="2"
            />
          </label>
          <label>
            Debit account <span>*</span>
            <select
              value={entry.debitAccount}
              onChange={(event) => updateField('debitAccount', event.target.value)}
              aria-label="Debit account"
            >
              {ACCOUNT_OPTIONS.map((account) => <option key={account}>{account}</option>)}
            </select>
          </label>
          <label>
            Credit account <span>*</span>
            <select
              value={entry.creditAccount}
              onChange={(event) => updateField('creditAccount', event.target.value)}
              aria-label="Credit account"
            >
              {ACCOUNT_OPTIONS.map((account) => <option key={account}>{account}</option>)}
            </select>
          </label>
          <label>
            Amount (Rs) <span>*</span>
            <input
              type="number"
              min="0.01"
              step="0.01"
              value={entry.amount}
              onChange={(event) => updateField('amount', event.target.value)}
              aria-label="Entry amount"
            />
          </label>
        </div>
        <div className="actions">
          <Button type="button" variant="secondary" onClick={onClose}>Cancel</Button>
          <Button type="submit">Save entry</Button>
        </div>
      </form>
    </div>
  )
}

export function ReceiveView({ branch, onComplete, onNotify }) {
  const [step, setStep] = useState(1)
  const [supplier, setSupplier] = useState('Apple Reseller LK')
  const [purchaseOrder, setPurchaseOrder] = useState('PO-518')
  const [category, setCategory] = useState('iPhone')
  const [productName, setProductName] = useState('')
  const [productCode, setProductCode] = useState('')
  const [quantityInput, setQuantityInput] = useState('1')
  const [totalBuyingPriceInput, setTotalBuyingPriceInput] = useState('')
  const [sellingPriceInput, setSellingPriceInput] = useState('')
  const [imeis, setImeis] = useState([])
  const [imeiError, setImeiError] = useState(false)
  const imeiRefs = useRef([])
  const quantity = Number(quantityInput)
  const totalBuyingPrice = Number(totalBuyingPriceInput)
  const buyingPricePerItem = quantity > 0 ? totalBuyingPrice / quantity : 0

  useEffect(() => {
    if (step === 2) imeiRefs.current[0]?.focus()
  }, [step])

  const goNext = () => {
    if (step === 1) {
      if (!productName.trim() || !productCode.trim()) {
        onNotify('Enter the product name and scan its product code')
        return
      }
      if (!Number.isInteger(quantity) || quantity < 1) {
        onNotify('Enter a quantity of at least one')
        return
      }
      if (!Number.isFinite(totalBuyingPrice) || totalBuyingPrice <= 0) {
        onNotify('Enter the total buying price')
        return
      }
      if (!Number.isFinite(Number(sellingPriceInput)) || Number(sellingPriceInput) <= 0) {
        onNotify('Enter the selling price per item')
        return
      }
      setImeis((current) => Array.from(
        { length: quantity },
        (_, index) => current[index] || '',
      ))
      setImeiError(false)
    } else if (step === 2) {
      const scannedImeis = imeis.map((value) => value.trim())
      if (scannedImeis.some((value) => !/^\d{15}$/.test(value))) {
        setImeiError(true)
        onNotify('Scan a valid 15-digit IMEI into every device field')
        return
      }
      if (new Set(scannedImeis).size !== scannedImeis.length) {
        setImeiError(true)
        onNotify('Each device must have a different IMEI')
        return
      }
      setImeiError(false)
    } else {
      onComplete({
        supplier,
        purchaseOrder,
        branch,
        category,
        productName: productName.trim(),
        productCode: productCode.trim(),
        quantity,
        totalBuyingPrice,
        buyingPricePerItem,
        sellingPricePerItem: Number(sellingPriceInput),
        devices: imeis.map((value) => ({
          productCode: productCode.trim(),
          imei: value.trim(),
        })),
      })
      return
    }
    setStep((current) => current + 1)
  }

  return (
    <>
      <PageHeader
        title="Receive stock"
        breadcrumbs={['Buy', 'Receive stock']}
        context="Check goods in against a purchase order."
      />
      <div className="steps">
        {['Product details', 'Scan IMEIs', 'Confirm'].map((label, index) => (
          <div className={`step${step === index + 1 ? ' active' : step > index + 1 ? ' complete' : ''}`} key={label}>
            <span>{step > index + 1 ? '✓' : index + 1}</span>{label}
          </div>
        ))}
      </div>
      <div className="card form-card">
        {step === 1 && (
          <div className="form-grid">
            <label htmlFor="device-category">Device category</label>
            <select id="device-category" value={category} onChange={(event) => setCategory(event.target.value)}>
              <option>iPhone</option>
              <option>Android</option>
              <option>Tablet</option>
              <option>Laptop</option>
              <option>Other</option>
            </select>
            <label htmlFor="product-name">Product / model</label>
            <input id="product-name" value={productName} onChange={(event) => setProductName(event.target.value)} />
            <label htmlFor="product-code">Product code / barcode</label>
            <input
              id="product-code"
              value={productCode}
              onChange={(event) => setProductCode(event.target.value)}
              autoComplete="off"
            />
            <label htmlFor="receive-quantity">Quantity</label>
            <input
              id="receive-quantity"
              type="number"
              min="1"
              step="1"
              value={quantityInput}
              onChange={(event) => setQuantityInput(event.target.value)}
            />
            <label htmlFor="total-buying-price">Total buying price (Rs)</label>
            <input
              id="total-buying-price"
              type="number"
              min="0"
              step="0.01"
              value={totalBuyingPriceInput}
              onChange={(event) => setTotalBuyingPriceInput(event.target.value)}
            />
            <label htmlFor="buying-price-per-item">Buying price per item (Rs)</label>
            <input id="buying-price-per-item" value={formatRupees(buyingPricePerItem)} readOnly />
            <label htmlFor="selling-price-per-item">Selling price per item (Rs)</label>
            <input
              id="selling-price-per-item"
              type="number"
              min="0"
              step="0.01"
              value={sellingPriceInput}
              onChange={(event) => setSellingPriceInput(event.target.value)}
            />
            <label htmlFor="supplier">Supplier</label>
            <select id="supplier" value={supplier} onChange={(event) => setSupplier(event.target.value)}>
              <option>Apple Reseller LK</option>
              <option>Singer Distributors</option>
            </select>
            <label htmlFor="purchase-order">Purchase order</label>
            <select id="purchase-order" value={purchaseOrder} onChange={(event) => setPurchaseOrder(event.target.value)}>
              <option>PO-518</option>
            </select>
          </div>
        )}
        {step === 2 && (
          <>
            <div className="form-grid">
              <p className="form-hint">
                {quantity} IMEI barcode field{quantity === 1 ? '' : 's'} for {productName}. Scan each device; press Enter to move to the next.
              </p>
              {imeis.map((value, index) => (
                <div className="imei-scan-field" key={index}>
                  <label htmlFor={`scan-imei-${index}`}>Device {index + 1} IMEI / barcode</label>
                  <input
                    id={`scan-imei-${index}`}
                    ref={(element) => { imeiRefs.current[index] = element }}
                    className={imeiError && !/^\d{15}$/.test(value.trim()) ? 'err' : ''}
                    value={value}
                    onChange={(event) => {
                      const nextValue = event.target.value
                      setImeis((current) => current.map((currentValue, imeiIndex) =>
                        imeiIndex === index ? nextValue : currentValue,
                      ))
                      setImeiError(false)
                    }}
                    onKeyDown={(event) => {
                      if (event.key === 'Enter') {
                        event.preventDefault()
                        imeiRefs.current[index + 1]?.focus()
                      }
                    }}
                    inputMode="numeric"
                    autoComplete="off"
                    aria-label={`IMEI barcode for device ${index + 1}`}
                  />
                </div>
              ))}
            </div>
          </>
        )}
        {step === 3 && (
          <div className="confirmation">
            <p><b>{supplier}</b>, {purchaseOrder}</p>
            <p><b>{category} · {productName}</b> ({productCode})</p>
            <p>{quantity} devices will be added to {branch} stock.</p>
            <p>Total buying price: <b>{formatRupees(totalBuyingPrice)}</b></p>
            <p>Buying price per item: <b>{formatRupees(buyingPricePerItem)}</b></p>
            <p>Selling price per item: <b>{formatRupees(Number(sellingPriceInput))}</b></p>
            <ul className="received-device-summary">
              {imeis.map((scannedImei, index) => (
                <li key={`${scannedImei}-${index}`}>
                  Device {index + 1} · IMEI <b>{scannedImei}</b>
                </li>
              ))}
            </ul>
          </div>
        )}
        <div className="form-actions">
          {step > 1 && (
            <Button type="button" variant="secondary" onClick={() => setStep((current) => current - 1)}>Back</Button>
          )}
          <Button type="button" onClick={goNext}>
            {step < 3 ? 'Continue' : 'Add to stock'}
          </Button>
        </div>
      </div>
    </>
  )
}

export function ImeiView({ initialImei, onSearch }) {
  const [imei, setImei] = useState(initialImei || '356938035643809')
  const record = IMEI_DATA[imei.trim()]

  const search = (event) => {
    event.preventDefault()
    onSearch(imei.trim())
  }

  return (
    <>
      <PageHeader title="IMEI lookup" breadcrumbs={['Stock', 'IMEI lookup']} context="See the full device history from one IMEI." />
      <form className="filter-bar imei-tools" onSubmit={search}>
        <label className="filter-search">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <circle cx="10.8" cy="10.8" r="6.8" />
            <path d="m16 16 5 5" />
          </svg>
          <input
            value={imei}
            onChange={(event) => setImei(event.target.value)}
            placeholder="Enter a 15-digit IMEI"
            aria-label="IMEI number"
          />
        </label>
        <Button type="submit">Look up device</Button>
      </form>
      {record ? (
        <div className="card device-card">
          <h2>{record.device}</h2>
          <p>IMEI {imei.trim()}</p>
          <ul className="timeline">
            {record.events.map(([title, detail]) => (
              <li key={title}><b>{title}</b><small>{detail}</small></li>
            ))}
          </ul>
        </div>
      ) : (
        <div className="card"><EmptyState title="No phone found" description="Check the IMEI number and try again." /></div>
      )}
    </>
  )
}

export function ReportsView({ branch }) {
  const brands = [['Apple', 82], ['Samsung', 64], ['Xiaomi', 41], ['Accessories', 27]]
  return (
    <>
      <PageHeader title="Reports" breadcrumbs={['Dashboard', 'Reports']} context={`Sales by brand this month · ${branch}`} />
      <div className="card report-card">
        <div className="section-title"><h2>Sales by brand</h2><span>This month</span></div>
        {brands.map(([name, value]) => (
          <div className="bar" key={name}>
            <em>{name}</em>
            <div className="bar-track"><i style={{ width: `${value * 5}px` }} /></div>
            <span>Rs {value * 10}k</span>
          </div>
        ))}
      </div>
    </>
  )
}

export function TillView({ onOpen }) {
  return (
    <>
      <PageHeader title="Till" breadcrumbs={['Sell', 'Till']} context="Cashiers see only the till." />
      <Button type="button" icon="+" onClick={onOpen}>Open the till</Button>
    </>
  )
}

export function RoleManagerView({ roles, builtInRoles, onSave, onDelete }) {
  const [formOpen, setFormOpen] = useState(false)
  const [editingRole, setEditingRole] = useState('')
  const [roleName, setRoleName] = useState('')
  const [groups, setGroups] = useState([])
  const [items, setItems] = useState([])
  const [home, setHome] = useState(false)
  const [reports, setReports] = useState(false)
  const [error, setError] = useState('')

  const resetForm = () => {
    setFormOpen(false)
    setEditingRole('')
    setRoleName('')
    setGroups([])
    setItems([])
    setHome(false)
    setReports(false)
    setError('')
  }

  const editRole = (name) => {
    const permissions = roles[name]
    setFormOpen(true)
    setEditingRole(name)
    setRoleName(name)
    setGroups([...permissions.groups])
    setItems([...permissions.items])
    setHome(permissions.home)
    setReports(permissions.reports)
    setError('')
  }

  const toggleGroup = (groupId) => {
    setGroups((current) => current.includes(groupId)
      ? current.filter((value) => value !== groupId)
      : [...current, groupId])
  }

  const toggleItem = (itemId) => {
    setItems((current) => current.includes(itemId)
      ? current.filter((value) => value !== itemId)
      : [...current, itemId])
  }

  const submit = (event) => {
    event.preventDefault()
    const normalizedName = roleName.trim()
    if (!normalizedName) {
      setError('Enter a role name.')
      return
    }
    if (!home && !reports && !groups.length && !items.length) {
      setError('Grant access to at least one section.')
      return
    }
    const duplicate = Object.keys(roles).some((name) =>
      name.toLocaleLowerCase() === normalizedName.toLocaleLowerCase() && name !== editingRole,
    )
    if (duplicate) {
      setError('A role with that name already exists.')
      return
    }
    const saved = onSave(normalizedName, { groups, items, home, reports })
    if (saved) resetForm()
  }

  return (
    <>
      <PageHeader
        title="Users and roles"
        breadcrumbs={['Admin', 'Users and roles']}
        context="Create roles and control which sections each role can open."
        action={<Button type="button" onClick={() => {
          resetForm()
          setFormOpen(true)
        }}>Create role</Button>}
      />
      {formOpen ? (
        <form className="card role-form" onSubmit={submit}>
          <h2>{editingRole ? `Edit ${editingRole}` : 'Create role'}</h2>
          {error && <p className="accounting-form-error" role="alert">{error}</p>}
          <label>
            Role name
            <input
              value={roleName}
              disabled={Boolean(editingRole)}
              onChange={(event) => setRoleName(event.target.value)}
              aria-label="Role name"
            />
          </label>
          <div className="role-permissions">
            <label><input type="checkbox" checked={home} onChange={(event) => setHome(event.target.checked)} /> Home</label>
            <label><input type="checkbox" checked={reports} onChange={(event) => setReports(event.target.checked)} /> Reports</label>
            {NAV_GROUPS.map((group) => (
              <label key={group.id}>
                <input
                  type="checkbox"
                  checked={groups.includes(group.id)}
                  onChange={() => toggleGroup(group.id)}
                />
                {group.name}
              </label>
            ))}
          </div>
          <h3 className="role-subheading">Specific pages</h3>
          <div className="role-permissions">
            {NAV_GROUPS.flatMap((group) => group.items
              .filter(([itemId]) => itemId !== 'users')
              .map(([itemId, itemName]) => (
              <label key={itemId}>
                <input
                  type="checkbox"
                  checked={items.includes(itemId)}
                  onChange={() => toggleItem(itemId)}
                />
                {itemName}
              </label>
            )))}
          </div>
          <div className="role-form-actions">
            <Button type="button" variant="secondary" onClick={resetForm}>Cancel</Button>
            <Button type="submit">Save role</Button>
          </div>
        </form>
      ) : (
        <div className="card role-form-empty">
          <p>Select a role to edit permissions, or create a new role.</p>
        </div>
      )}
      <DataTable columns={['Role', 'Access', 'Actions']} className="role-table">
        {Object.entries(roles).map(([name, permissions]) => (
          <tr key={name}>
            <td><b>{name}</b>{name === 'Owner' && <small className="role-note">Full access</small>}</td>
            <td>{[
              permissions.home && 'Home',
              permissions.reports && 'Reports',
              ...NAV_GROUPS.filter((group) => permissions.groups.includes(group.id)).map((group) => group.name),
              ...NAV_GROUPS.flatMap((group) => group.items
                .filter(([itemId]) => itemId !== 'users' && permissions.items.includes(itemId) && !permissions.groups.includes(group.id))
                .map(([, itemName]) => itemName)),
            ].filter(Boolean).join(', ') || 'No access'}</td>
            <td className="role-actions">
              {name !== 'Owner' && (
                <Button type="button" variant="secondary" onClick={() => editRole(name)}>Edit</Button>
              )}
              {!builtInRoles.includes(name) && name !== 'Owner' && (
                <Button type="button" variant="secondary" onClick={() => onDelete(name)}>Delete</Button>
              )}
            </td>
          </tr>
        ))}
      </DataTable>
    </>
  )
}

export function HRPayrollView({
  employees,
  commissionRates,
  sales,
  accountingEntries,
  onAddEmployee,
  onSaveCommissionRates,
}) {
  const [employeeName, setEmployeeName] = useState('')
  const [employeeCode, setEmployeeCode] = useState('')
  const [employeeError, setEmployeeError] = useState('')
  const [rateInputs, setRateInputs] = useState(() => Object.fromEntries(
    COMMISSION_CATEGORIES.map((category) => [category, String(commissionRates[category] || 0)]),
  ))
  const [rateMessage, setRateMessage] = useState('')

  const saveEmployee = (event) => {
    event.preventDefault()
    const saved = onAddEmployee(employeeName, employeeCode)
    if (saved) {
      setEmployeeName('')
      setEmployeeCode('')
      setEmployeeError('')
    } else {
      setEmployeeError('Employee could not be saved. Check the message and try again.')
    }
  }

  const saveRates = (event) => {
    event.preventDefault()
    const rates = Object.fromEntries(COMMISSION_CATEGORIES.map((category) => [
      category,
      Number(rateInputs[category]),
    ]))
    if (Object.values(rates).some((rate) => !Number.isFinite(rate) || rate < 0)) {
      setRateMessage('Commission rates must be zero or a positive amount.')
      return
    }
    const saved = onSaveCommissionRates(rates)
    setRateMessage(saved ? 'Commission rates saved.' : 'Commission rates could not be saved.')
  }

  const payrollRows = employees.map((employee) => {
    const earned = sales
      .filter((sale) => sale.employeeId === employee.id)
      .reduce((sum, sale) => sum + Number(sale.commissionTotal || 0), 0)
    const reversed = accountingEntries
      .filter((entry) => entry.source === 'Commission reversal' && entry.employeeId === employee.id)
      .reduce((sum, entry) => sum + Number(entry.amount || 0), 0)
    return { ...employee, earned, reversed, payable: earned - reversed }
  })

  return (
    <>
      <PageHeader
        title="HR and payroll"
        breadcrumbs={['People', 'HR and payroll']}
        context="Manage employees and fixed per-unit sales commission."
      />
      <div className="hr-panels">
        <section className="card hr-card">
          <h2>Employees</h2>
          <form className="hr-employee-form" onSubmit={saveEmployee}>
            <label>
              Employee name
              <input value={employeeName} onChange={(event) => setEmployeeName(event.target.value)} />
            </label>
            <label>
              Employee ID <small>(optional)</small>
              <input value={employeeCode} onChange={(event) => setEmployeeCode(event.target.value)} />
            </label>
            <Button type="submit">Add employee</Button>
          </form>
          {employeeError && <p className="accounting-form-error" role="alert">{employeeError}</p>}
          <DataTable columns={['Name', 'Employee ID']}>
            {employees.map((employee) => (
              <tr key={employee.id}><td>{employee.name}</td><td>{employee.employeeCode || '—'}</td></tr>
            ))}
            {!employees.length && <tr><td className="table-empty" colSpan="2">Add employees to assign sales and commissions.</td></tr>}
          </DataTable>
        </section>
        <section className="card hr-card">
          <h2>Commission rates</h2>
          <p>Set the fixed amount earned for each unit sold. Refunds reverse commission in proportion to the refund amount.</p>
          <form className="commission-form" onSubmit={saveRates}>
            {COMMISSION_CATEGORIES.map((category) => (
              <label key={category}>
                {category} (Rs / unit)
                <input
                  type="number"
                  min="0"
                  step="0.01"
                  value={rateInputs[category]}
                  onChange={(event) => setRateInputs((current) => ({
                    ...current,
                    [category]: event.target.value,
                  }))}
                />
              </label>
            ))}
            <Button type="submit">Save rates</Button>
          </form>
          {rateMessage && <p className="form-hint" role="status">{rateMessage}</p>}
        </section>
      </div>
      <h2 className="section-title">Commission payroll</h2>
      <DataTable columns={['Employee', 'Commission earned', 'Refund reversals', 'Net payable']}>
        {payrollRows.map((employee) => (
          <tr key={employee.id}>
            <td>{employee.name}</td>
            <td>{formatRupees(employee.earned)}</td>
            <td>{formatRupees(employee.reversed)}</td>
            <td><b>{formatRupees(employee.payable)}</b></td>
          </tr>
        ))}
        {!payrollRows.length && <tr><td className="table-empty" colSpan="4">Add an employee to start tracking commission payroll.</td></tr>}
      </DataTable>
    </>
  )
}

export function PlaceholderView({ title, branch, onAction }) {
  const isAccounting = title === 'Accounting'
  const isAdmin = ['Settings', 'Users and roles', 'HR and payroll'].includes(title)
  const description = isAccounting
    ? 'Start by creating a new accounting entry or adjust your filters.'
    : isAdmin
      ? 'This module is ready for your organization’s records and settings.'
      : `No ${title.toLowerCase()} records are available for the ${branch} branch yet.`

  return (
    <>
      <PageHeader
        title={title}
        breadcrumbs={['Dashboard', title]}
        action={isAccounting && <Button type="button" icon="+" onClick={onAction}>New Entry</Button>}
      />
      <div className={`card placeholder-panel${isAccounting ? ' accounting-panel' : ''}`}>
        {isAccounting && (
          <div className="info-callout">
            <span className="info-icon">i</span>
            <span>This section follows the same layout as the other pages: search and filters on top, a table below, and <b>“New”</b> at the top right.</span>
          </div>
        )}
        <EmptyState
          title={isAdmin ? `${title} setup` : 'No Records Yet'}
          description={description}
          illustration={isAccounting}
          actionLabel={isAccounting ? 'Create New Entry' : undefined}
          onAction={isAccounting ? onAction : undefined}
        />
      </div>
    </>
  )
}
