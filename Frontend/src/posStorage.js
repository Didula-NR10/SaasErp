const STORAGE_KEY = 'saas2-pos-records'

function emptyRecords() {
  return { customers: [], sales: [] }
}

export function normalizePhone(phone) {
  const digits = phone.replace(/\D/g, '')
  return digits.startsWith('94') && digits.length === 11
    ? `0${digits.slice(2)}`
    : digits
}

export function readPosRecords() {
  let serialized
  try {
    serialized = window.localStorage.getItem(STORAGE_KEY)
  } catch {
    throw new Error('Browser storage is unavailable.')
  }

  if (!serialized) return emptyRecords()

  let records
  try {
    records = JSON.parse(serialized)
  } catch {
    throw new Error('Saved till records could not be read.')
  }

  if (
    !records ||
    !Array.isArray(records.customers) ||
    !records.customers.every((customer) =>
      customer &&
      typeof customer.name === 'string' &&
      typeof customer.phone === 'string' &&
      typeof customer.address === 'string',
    ) ||
    !Array.isArray(records.sales) ||
    !records.sales.every((sale) => sale && typeof sale === 'object')
  ) {
    throw new Error('Saved till records have an invalid format.')
  }

  return records
}

export function savePosSale(customerDetails, saleDetails) {
  const records = readPosRecords()
  const phoneKey = normalizePhone(customerDetails.phone)
  const existingIndex = records.customers.findIndex(
    (customer) => normalizePhone(customer.phone) === phoneKey,
  )
  const timestamp = new Date().toISOString()
  const previousCustomer = existingIndex < 0 ? null : records.customers[existingIndex]
  const customer = {
    ...customerDetails,
    email: customerDetails.email || previousCustomer?.email || '',
    nic: customerDetails.nic || previousCustomer?.nic || '',
    id: previousCustomer?.id || `customer-${Date.now()}`,
    createdAt: previousCustomer?.createdAt || timestamp,
    updatedAt: timestamp,
  }
  const invoice = `INV-${1046 + records.sales.length}`
  const sale = {
    ...saleDetails,
    status: saleDetails.status || 'Paid',
    invoice,
    customer: { ...customer },
    createdAt: timestamp,
  }

  if (existingIndex < 0) records.customers.push(customer)
  else records.customers[existingIndex] = customer
  records.sales.push(sale)

  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(records))
  return { customer, invoice, sale }
}
