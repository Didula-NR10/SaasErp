const STORAGE_KEY = 'saas2-suppliers'

export function readSuppliers() {
  let serialized
  try {
    serialized = window.localStorage.getItem(STORAGE_KEY)
  } catch {
    throw new Error('Browser storage is unavailable.')
  }

  if (!serialized) return []

  let suppliers
  try {
    suppliers = JSON.parse(serialized)
  } catch {
    throw new Error('Saved suppliers could not be read.')
  }

  if (
    !Array.isArray(suppliers) ||
    !suppliers.every((supplier) =>
      supplier &&
      typeof supplier.name === 'string' &&
      typeof supplier.contactName === 'string' &&
      typeof supplier.phone === 'string' &&
      typeof supplier.email === 'string' &&
      typeof supplier.address === 'string' &&
      typeof supplier.taxNumber === 'string' &&
      typeof supplier.paymentTerms === 'string',
    )
  ) {
    throw new Error('Saved suppliers have an invalid format.')
  }

  return suppliers
}

export function saveSupplier(details) {
  const suppliers = readSuppliers()
  const phoneKey = details.phone.replace(/\D/g, '')
  if (suppliers.some((supplier) => supplier.phone.replace(/\D/g, '') === phoneKey)) {
    throw new Error('A supplier with this phone number already exists.')
  }

  const supplier = {
    ...details,
    id: `supplier-${Date.now()}`,
    status: 'Active',
    balance: 0,
    createdAt: new Date().toISOString(),
  }

  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify([...suppliers, supplier]))
  } catch {
    throw new Error('Supplier could not be saved to browser storage.')
  }

  return supplier
}
