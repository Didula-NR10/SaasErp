const STORAGE_KEY = 'saas2-hr-data'
export const COMMISSION_CATEGORIES = ['Phones', 'Laptops', 'Accessories']

function emptyHrData() {
  return {
    employees: [],
    commissionRates: Object.fromEntries(COMMISSION_CATEGORIES.map((category) => [category, 0])),
  }
}

function validateHrData(data) {
  return data
    && Array.isArray(data.employees)
    && data.employees.every((employee) =>
      employee
      && typeof employee.id === 'string'
      && typeof employee.name === 'string'
      && typeof employee.employeeCode === 'string',
    )
    && data.commissionRates
    && COMMISSION_CATEGORIES.every((category) =>
      Number.isFinite(data.commissionRates[category]) && data.commissionRates[category] >= 0,
    )
}

export function readHrData() {
  const stored = localStorage.getItem(STORAGE_KEY)
  if (!stored) return emptyHrData()

  const parsed = JSON.parse(stored)
  if (!validateHrData(parsed)) throw new Error('Saved HR and commission data is invalid')
  return parsed
}

function writeHrData(data) {
  if (!validateHrData(data)) throw new Error('HR and commission data is invalid')
  localStorage.setItem(STORAGE_KEY, JSON.stringify(data))
  return data
}

export function saveEmployee(name, employeeCode) {
  const normalizedName = name.trim()
  const normalizedCode = employeeCode.trim()
  if (!normalizedName) throw new Error('Employee name is required')

  const data = readHrData()
  if (data.employees.some((employee) =>
    employee.name.toLocaleLowerCase() === normalizedName.toLocaleLowerCase()
    || (normalizedCode && employee.employeeCode.toLocaleLowerCase() === normalizedCode.toLocaleLowerCase()),
  )) {
    throw new Error('An employee with that name or ID already exists')
  }

  data.employees.push({
    id: `employee-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    name: normalizedName,
    employeeCode: normalizedCode,
  })
  return writeHrData(data)
}

export function saveCommissionRates(commissionRates) {
  const data = readHrData()
  data.commissionRates = Object.fromEntries(
    COMMISSION_CATEGORIES.map((category) => [category, Number(commissionRates[category])]),
  )
  return writeHrData(data)
}
