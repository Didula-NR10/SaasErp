import { useEffect, useMemo, useRef, useState } from 'react'
import { POS_PRODUCTS, formatRupees } from '../data'
import { savePosSale } from '../posStorage'
import { Button } from './Ui'

const CATEGORIES = ['All', 'Phones', 'Laptops', 'Accessories']
const PAYMENT_METHODS = ['Cash', 'Card', 'Wallet', 'Installment']
const EMPTY_CUSTOMER = { name: '', phone: '', address: '', email: '', nic: '' }

function ProductCatalog({ category, query, onCategoryChange, onAdd }) {
  const products = useMemo(() => POS_PRODUCTS
    .map((product, index) => ({ product, index }))
    .filter(({ product }) =>
      (category === 'All' || product[3] === category) &&
      product[0].toLowerCase().includes(query.toLowerCase()),
    ), [category, query])

  return (
    <>
      <div className="categories">
        {CATEGORIES.map((item) => (
          <button
            className={item === category ? 'on' : ''}
            type="button"
            key={item}
            onClick={() => onCategoryChange(item)}
          >
            {item}
          </button>
        ))}
      </div>
      <div className="pos-grid">
        {products.map(({ product, index }) => (
          <button type="button" data-product={index} key={product[0]} onClick={() => onAdd(index)}>
            <b>{product[0]}</b>
            <small>{formatRupees(product[1])}</small>
            <small>{product[2] ? 'Needs IMEI' : 'By quantity'}</small>
          </button>
        ))}
      </div>
    </>
  )
}

function SaleCart({ cart, onRemove, onQuantityChange, onImeiChange }) {
  return (
    <div className="pos-cart">
      {cart.length ? cart.map((line, index) => (
        <div className="sale-line" key={`${line.name}-${index}`}>
          <div className="sale-line-top">
            <span>{line.name}{line.imeiRequired ? '' : ` x${line.quantity}`}</span>
            <span>
              {formatRupees(line.price * line.quantity)}{' '}
              <button
                className="remove-line"
                type="button"
                aria-label={`Remove ${line.name}`}
                onClick={() => onRemove(index)}
              >
                ✕
              </button>
            </span>
          </div>
          {line.imeiRequired ? (
            <input
              className={line.error ? 'err' : ''}
              value={line.imei}
              onChange={(event) => onImeiChange(index, event.target.value)}
              placeholder="Scan IMEI"
              aria-label={`IMEI for ${line.name}`}
            />
          ) : (
            <div className="quantity-controls">
              <button type="button" aria-label={`Decrease ${line.name}`} onClick={() => onQuantityChange(index, -1)}>−</button>
              <span>{line.quantity}</span>
              <button type="button" aria-label={`Increase ${line.name}`} onClick={() => onQuantityChange(index, 1)}>+</button>
            </div>
          )}
        </div>
      )) : <p className="pos-empty">Nothing in the sale yet. Scan or tap an item.</p>}
    </div>
  )
}

export function PosPanel({
  isOpen,
  branch,
  employees = [],
  commissionRates = {},
  discount,
  onDiscountChange,
  onRequestDiscount,
  onNotify,
  onSaleComplete,
  onClose,
}) {
  const [category, setCategory] = useState('All')
  const [query, setQuery] = useState('')
  const [cart, setCart] = useState([])
  const [paymentMethods, setPaymentMethods] = useState(['Cash'])
  const [paymentAmounts, setPaymentAmounts] = useState({})
  const [customer, setCustomer] = useState(EMPTY_CUSTOMER)
  const [employeeId, setEmployeeId] = useState('')
  const [customerError, setCustomerError] = useState(false)
  const [customerDialogOpen, setCustomerDialogOpen] = useState(false)
  const searchRef = useRef(null)
  const customerNameRef = useRef(null)

  const subtotal = cart.reduce((total, line) => total + line.price * line.quantity, 0)
  const total = subtotal * (1 - discount / 100)
  const commissionTotal = Math.round(cart.reduce((sum, line) =>
    sum + Number(commissionRates[line.category] || 0) * line.quantity, 0) * 100) / 100
  const selectedEmployee = employees.find((employee) => employee.id === employeeId)
  const paymentAllocations = paymentMethods.length === 1
    ? [{ method: paymentMethods[0], amount: total }]
    : paymentMethods.map((method) => ({
      method,
      amount: Math.round(Number(paymentAmounts[method] || 0) * 100) / 100,
    }))
  const allocatedTotal = paymentAllocations.reduce((sum, allocation) => sum + allocation.amount, 0)

  useEffect(() => {
    if (isOpen) searchRef.current?.focus()
  }, [isOpen])

  useEffect(() => {
    if (customerDialogOpen) customerNameRef.current?.focus()
  }, [customerDialogOpen])

  useEffect(() => {
    const handlePayShortcut = (event) => {
      if (customerDialogOpen && event.key === 'Escape') {
        event.preventDefault()
        event.stopPropagation()
        setCustomerDialogOpen(false)
      } else if (isOpen && event.key === 'F9') {
        event.preventDefault()
        requestPayment()
      }
    }
    window.addEventListener('keydown', handlePayShortcut, true)
    return () => window.removeEventListener('keydown', handlePayShortcut, true)
  })

  const addProduct = (productIndex) => {
    const [name, price, imeiRequired, category] = POS_PRODUCTS[productIndex]
    if (imeiRequired) {
      setCart((current) => [...current, {
        name,
        price,
        category,
        quantity: 1,
        imeiRequired: true,
        imei: '',
        error: false,
      }])
    } else {
      setCart((current) => {
        const existingIndex = current.findIndex((line) => line.name === name)
        if (existingIndex < 0) {
          return [...current, { name, price, category, quantity: 1, imeiRequired: false, imei: '' }]
        }
        return current.map((line, index) => index === existingIndex
          ? { ...line, quantity: line.quantity + 1 }
          : line)
      })
    }
  }

  const updateLine = (index, update) => {
    setCart((current) => current.map((line, lineIndex) =>
      lineIndex === index ? { ...line, ...update } : line,
    ))
  }

  const changeQuantity = (index, delta) => {
    setCart((current) => current.flatMap((line, lineIndex) => {
      if (lineIndex !== index) return [line]
      const quantity = line.quantity + delta
      return quantity > 0 ? [{ ...line, quantity }] : []
    }))
  }

  const chooseDiscount = (event) => {
    const amount = Number(event.target.value)
    if (amount > 5) onRequestDiscount(amount)
    else onDiscountChange(amount)
  }

  const togglePayment = (method) => {
    setPaymentMethods((current) => {
      if (current.includes(method)) {
        return current.length > 1 ? current.filter((value) => value !== method) : current
      }
      return [...current, method]
    })
  }

  function requestPayment() {
    if (!cart.length) {
      onNotify('Add an item first')
      return
    }
    setCustomerError(false)
    setCustomerDialogOpen(true)
  }

  function completeSale() {
    const requiredCustomerFields = ['name', 'phone', 'address']
    if (
      requiredCustomerFields.some((field) => !customer[field].trim()) ||
      customer.phone.replace(/\D/g, '').length < 7 ||
      (customer.email.trim() && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(customer.email.trim()))
    ) {
      setCustomerError(true)
      onNotify('Check the customer name, phone, address, and email')
      return
    }
    const invalidLines = cart
      .map((line, index) => ({ line, index }))
      .filter(({ line }) => line.imeiRequired && line.imei.trim().length < 15)
    if (invalidLines.length) {
      setCart((current) => current.map((line, index) => ({
        ...line,
        error: invalidLines.some(({ index: invalidIndex }) => invalidIndex === index),
      })))
      onNotify('Enter a 15-digit IMEI for each phone')
      return
    }
    if (
      paymentAllocations.some((allocation) => !Number.isFinite(allocation.amount) || allocation.amount <= 0) ||
      Math.round(allocatedTotal * 100) !== Math.round(total * 100)
    ) {
      onNotify(`Allocate the full ${formatRupees(total)} across the selected payment methods`)
      return
    }
    if (commissionTotal > 0 && !selectedEmployee) {
      onNotify('Select the salesperson to calculate this sale’s commission')
      return
    }
    let savedSale
    try {
      savedSale = savePosSale(
        Object.fromEntries(Object.entries(customer).map(([field, value]) => [field, value.trim()])),
        {
          items: cart.map(({ error: _error, ...line }) => ({
            ...line,
            commissionRate: Number(commissionRates[line.category] || 0),
            commissionAmount: Math.round(Number(commissionRates[line.category] || 0) * line.quantity * 100) / 100,
          })),
          employeeId: selectedEmployee?.id || '',
          employeeName: selectedEmployee?.name || '',
          commissionTotal,
          subtotal,
          discount,
          total,
          paymentMethods: [...paymentMethods],
          paymentAllocations,
          branch,
        },
      )
    } catch {
      onNotify('Sale could not be saved. Check browser storage and try again.')
      return
    }
    const auditError = onSaleComplete(savedSale)
    const split = paymentMethods.length > 1 ? `, split across ${paymentMethods.length} methods` : ''
    onNotify(auditError
      ? `Sale ${savedSale.invoice} completed, but its audit needs attention: ${auditError}`
      : `Sale complete: ${savedSale.invoice}${split}`)
    setCart([])
    setCustomer(EMPTY_CUSTOMER)
    setEmployeeId('')
    setCustomerError(false)
    setCustomerDialogOpen(false)
    setPaymentAmounts({})
    onDiscountChange(0)
  }

  return (
    <section className={`pos${isOpen ? ' open' : ''}`} aria-label="Point of sale" aria-hidden={!isOpen}>
      <div className="pos-left">
        <div className="pos-header">
          <Button type="button" variant="secondary" onClick={onClose}>Exit till</Button>
          <input
            id="pos-search"
            ref={searchRef}
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Scan a barcode or search items ( / )"
            autoComplete="off"
          />
          <span className="pill" title="Sales are saved on this device if the internet drops">Online</span>
        </div>
        <ProductCatalog
          category={category}
          query={query}
          onCategoryChange={setCategory}
          onAdd={addProduct}
        />
        <p className="hint">Shortcuts: / search, F9 pay, Esc exit till</p>
      </div>
      <div className="pos-right">
        <b className="sale-title">Current sale</b>
        <SaleCart
          cart={cart}
          onRemove={(index) => setCart((current) => current.filter((_, itemIndex) => itemIndex !== index))}
          onQuantityChange={changeQuantity}
          onImeiChange={(index, imei) => updateLine(index, { imei, error: false })}
        />
        <div className="summary">
          <span>Discount</span>
          <select value={discount} onChange={chooseDiscount} aria-label="Discount">
            <option value="0">None</option>
            <option value="5">5</option>
            <option value="10">10</option>
            <option value="15">15</option>
          </select>
        </div>
        <div className="summary"><span>Subtotal</span><span>{formatRupees(subtotal)}</span></div>
        <div className="summary grand-total"><span>Total</span><span>{formatRupees(total)}</span></div>
        {commissionTotal > 0 && (
          <div className="summary"><span>Staff commission</span><span>{formatRupees(commissionTotal)}</span></div>
        )}
        <div className="payment-methods">
          {PAYMENT_METHODS.map((method) => (
            <button
              className={paymentMethods.includes(method) ? 'on' : ''}
              type="button"
              key={method}
              onClick={() => togglePayment(method)}
            >
              {method}
            </button>
          ))}
        </div>
        {paymentMethods.length > 1 && (
          <div className="payment-split">
            <b>Split payment</b>
            {paymentMethods.map((method) => (
              <label key={method}>
                <span>{method}</span>
                <input
                  type="number"
                  min="0.01"
                  step="0.01"
                  value={paymentAmounts[method] || ''}
                  onChange={(event) => setPaymentAmounts((current) => ({
                    ...current,
                    [method]: event.target.value,
                  }))}
                  aria-label={`${method} payment amount`}
                />
              </label>
            ))}
            <small>
              Allocated: {formatRupees(allocatedTotal)} / {formatRupees(total)}
            </small>
          </div>
        )}
        <Button className="pay-button" type="button" onClick={requestPayment}>Pay (F9)</Button>
      </div>
      {customerDialogOpen && (
        <div className="modal checkout-modal" role="presentation" onMouseDown={(event) => {
          if (event.target === event.currentTarget) setCustomerDialogOpen(false)
        }}>
          <form
            className="modal-box checkout-modal-box"
            role="dialog"
            aria-modal="true"
            aria-labelledby="customer-modal-title"
            noValidate
            onSubmit={(event) => {
              event.preventDefault()
              completeSale()
            }}
          >
            <h2 id="customer-modal-title">Customer details</h2>
            <p>Enter the customer information to complete this sale.</p>
            <div className="customer-modal-fields">
              <label>
                Salesperson {commissionTotal > 0 && <span>*</span>}
                <select
                  value={employeeId}
                  onChange={(event) => setEmployeeId(event.target.value)}
                  aria-label="Salesperson"
                >
                  <option value="">Select employee</option>
                  {employees.map((employee) => <option key={employee.id} value={employee.id}>{employee.name}</option>)}
                </select>
                {!employees.length && <small>Add an employee in HR before selling commission-eligible items.</small>}
              </label>
              <label>
                Name <span>*</span>
                <input
                  ref={customerNameRef}
                  className={customerError && !customer.name.trim() ? 'err' : ''}
                  autoComplete="name"
                  value={customer.name}
                  onChange={(event) => setCustomer((current) => ({ ...current, name: event.target.value }))}
                  aria-label="Customer name"
                />
              </label>
              <label>
                Phone <span>*</span>
                <input
                  className={customerError && customer.phone.replace(/\D/g, '').length < 7 ? 'err' : ''}
                  type="tel"
                  autoComplete="tel"
                  value={customer.phone}
                  onChange={(event) => setCustomer((current) => ({ ...current, phone: event.target.value }))}
                  aria-label="Customer phone"
                />
              </label>
              <label className="customer-address-field">
                Address <span>*</span>
                <textarea
                  className={customerError && !customer.address.trim() ? 'err' : ''}
                  autoComplete="street-address"
                  value={customer.address}
                  onChange={(event) => setCustomer((current) => ({ ...current, address: event.target.value }))}
                  aria-label="Customer address"
                  rows="3"
                />
              </label>
              <label>
                Email <small>(optional)</small>
                <input
                  className={customerError && customer.email.trim() && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(customer.email.trim()) ? 'err' : ''}
                  type="email"
                  autoComplete="email"
                  value={customer.email}
                  onChange={(event) => setCustomer((current) => ({ ...current, email: event.target.value }))}
                  aria-label="Customer email"
                />
              </label>
              <label>
                NIC <small>(optional)</small>
                <input
                  value={customer.nic}
                  onChange={(event) => setCustomer((current) => ({ ...current, nic: event.target.value }))}
                  aria-label="Customer NIC"
                />
              </label>
            </div>
            <div className="checkout-modal-total">
              <span>{cart.length} {cart.length === 1 ? 'item' : 'items'}</span>
              <b>{formatRupees(total)}</b>
            </div>
            <div className="actions">
              <Button type="button" variant="secondary" onClick={() => setCustomerDialogOpen(false)}>Cancel</Button>
              <Button type="submit">Complete sale</Button>
            </div>
          </form>
        </div>
      )}
    </section>
  )
}
