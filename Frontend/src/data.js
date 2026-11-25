export const NAV_GROUPS = [
  {
    id: 'sell',
    name: 'Sell',
    color: '#2fa7e0',
    items: [
      ['pos', 'Open the till'],
      ['invoices', 'Invoices'],
      ['installments', 'Installments'],
      ['customers', 'Customers and loyalty'],
    ],
  },
  {
    id: 'stock',
    name: 'Stock',
    color: '#e0a62f',
    items: [
      ['inventory', 'Inventory'],
      ['imei', 'IMEI lookup'],
      ['transfers', 'Transfers'],
      ['counts', 'Stock counts'],
    ],
  },
  {
    id: 'buy',
    name: 'Buy',
    color: '#9a6be0',
    items: [
      ['purchases', 'Purchase orders'],
      ['receive', 'Receive stock'],
      ['suppliers', 'Suppliers'],
    ],
  },
  {
    id: 'after',
    name: 'After-sales',
    color: '#e0622f',
    items: [
      ['warranty', 'Warranty'],
      ['repairs', 'Repairs'],
      ['tradeins', 'Trade-ins'],
    ],
  },
  {
    id: 'money',
    name: 'Money',
    color: '#3dc486',
    items: [
      ['accounting', 'Accounting'],
      ['expenses', 'Expenses'],
      ['vat', 'VAT'],
    ],
  },
  {
    id: 'admin',
    name: 'People and admin',
    color: '#8b97a8',
    items: [
      ['hr', 'HR and payroll'],
      ['users', 'Users and roles'],
      ['settings', 'Settings'],
    ],
  },
]

export const ROLES = {
  Owner: { groups: ['sell', 'stock', 'buy', 'after', 'money', 'admin'], items: [], home: true, reports: true },
  Manager: { groups: ['sell', 'stock', 'after'], items: [], home: true, reports: true },
  Accountant: { groups: ['money', 'buy'], items: [], home: true, reports: true },
  Technician: { groups: [], items: ['repairs'], home: false, reports: false },
  Cashier: { groups: [], items: ['pos'], home: false, reports: false },
}

export const PAGE_DATA = {
  invoices: {
    title: 'Invoices',
    action: 'New invoice',
    statusIndex: 5,
    columns: ['Invoice', 'Customer', 'Date', 'Time', 'Total', 'Status', ''],
    rows: [
      ['INV-1042', 'Nimal Perera', '2 Nov', '—', 'Rs 289,000', 'Paid', 'Refund'],
      ['INV-1043', 'Dilani Fernando', '3 Nov', '—', 'Rs 124,500', 'Partial', 'Refund'],
      ['INV-1044', 'Walk-in', '3 Nov', '—', 'Rs 4,500', 'Paid', 'Refund'],
      ['INV-1045', 'Sunil Traders', '4 Nov', '—', 'Rs 712,000', 'Unpaid', 'Refund'],
    ],
  },
  installments: {
    title: 'Installments',
    action: 'New plan',
    statusIndex: 4,
    columns: ['Customer', 'Item', 'Monthly', 'Next due', 'Status'],
    rows: [
      ['Dilani Fernando', 'Galaxy S24', 'Rs 18,500', '25 Nov', 'Partial'],
      ['Kasun Silva', 'iPhone 15', 'Rs 24,000', '12 Nov', 'Overdue'],
      ['Ayesha Raheem', 'Redmi Note 13', 'Rs 9,800', '30 Nov', 'Paid'],
      ['Ruwan Jay', 'MacBook Air', 'Rs 31,000', '8 Nov', 'Overdue'],
    ],
  },
  customers: {
    title: 'Customers and loyalty',
    action: 'New customer',
    statusIndex: 6,
    columns: ['Name', 'Phone', 'Email', 'Address', 'NIC', 'Points', 'Status'],
    rows: [
      ['Nimal Perera', '077 123 4567', '1,240', 'Active'],
      ['Dilani Fernando', '071 555 0192', '860', 'Active'],
      ['Kasun Silva', '076 889 2210', '90', 'Overdue'],
    ],
  },
  inventory: {
    title: 'Inventory',
    action: 'New item',
    statusIndex: 7,
    columns: ['Item / product code', 'IMEI / tracking', 'Colombo 03', 'Kandy', 'Galle', 'Buying / item', 'Selling / item', 'Status'],
    rows: [
      ['iPhone 15 128GB Black', 'IMEI', '6', '2', '0', '—', '—', 'In stock'],
      ['Galaxy S24 256GB', 'IMEI', '3', '1', '0', '—', '—', 'Low'],
      ['Redmi Note 13', 'IMEI', '14', '9', '0', '—', '—', 'In stock'],
      ['Anker 20W charger', 'Quantity', '48', '20', '0', '—', '—', 'In stock'],
      ['Clear case, iPhone 15', 'Quantity', '0', '4', '0', '—', '—', 'Out of stock'],
    ],
  },
  transfers: {
    title: 'Transfers',
    action: 'New transfer',
    statusIndex: 4,
    columns: ['Ref', 'From', 'To', 'Items', 'Status'],
    rows: [
      ['TR-210', 'Colombo 03', 'Kandy', '5 phones', 'In transit'],
      ['TR-209', 'Kandy', 'Galle', '2 laptops', 'Received'],
    ],
  },
  purchases: {
    title: 'Purchase orders',
    action: 'New order',
    statusIndex: 4,
    columns: ['PO', 'Supplier', 'Date', 'Total', 'Status'],
    rows: [
      ['PO-518', 'Singer Distributors', '1 Nov', 'Rs 2,140,000', 'Pending'],
      ['PO-517', 'Apple Reseller LK', '27 Oct', 'Rs 3,860,000', 'Received'],
    ],
  },
  suppliers: {
    title: 'Suppliers',
    action: 'New supplier',
    statusIndex: 8,
    columns: ['Supplier', 'Contact', 'Phone', 'Email', 'Address', 'Tax/VAT ID', 'Payment terms', 'We owe', 'Status'],
    rows: [
      ['Singer Distributors', '—', '011 244 0000', '—', '—', '—', '—', 'Rs 2,140,000', 'Pending'],
      ['Apple Reseller LK', '—', '011 230 1100', '—', '—', '—', '—', 'Rs 0', 'Paid'],
    ],
  },
  warranty: {
    title: 'Warranty',
    action: 'New claim',
    statusIndex: 4,
    columns: ['IMEI', 'Item', 'Customer', 'Ends', 'Status'],
    rows: [
      ['356938035643809', 'iPhone 15', 'Nimal Perera', '2 Nov 2027', 'Active'],
      ['351234567890123', 'Galaxy S23', 'Sunil Traders', '14 Nov 2026', 'Pending'],
      ['359876543210987', 'Redmi 12', 'Walk-in', '1 Oct 2026', 'Expired'],
    ],
  },
  repairs: {
    title: 'Repairs',
    action: 'New job card',
    statusIndex: 4,
    columns: ['Job', 'Device', 'Fault', 'Technician', 'Status'],
    rows: [
      ['JC-77', 'iPhone 13', 'Screen', 'Chamara', 'In repair'],
      ['JC-78', 'Galaxy A54', 'Battery', 'Chamara', 'Ready'],
      ['JC-79', 'Redmi 12', 'No power', 'Waiting for tech', 'Waiting'],
    ],
  },
  tradeins: {
    title: 'Trade-ins',
    action: 'New trade-in',
    statusIndex: 4,
    columns: ['Ref', 'Device', 'Grade', 'Offer', 'Status'],
    rows: [
      ['TI-31', 'iPhone 12', 'B', 'Rs 98,000', 'Pending'],
      ['TI-30', 'Galaxy S21', 'A', 'Rs 74,000', 'Done'],
    ],
  },
  expenses: {
    title: 'Expenses',
    action: 'New expense',
    statusIndex: 3,
    columns: ['Date', 'Type', 'Amount', 'Status'],
    rows: [
      ['5 Nov', 'Shop rent', 'Rs 180,000', 'Paid'],
      ['6 Nov', 'Electricity', 'Rs 42,300', 'Pending'],
    ],
  },
}

export const IMEI_DATA = {
  '356938035643809': {
    device: 'iPhone 15 128GB Black',
    events: [
      ['Received', '12 Oct, from Apple Reseller LK (PO-517)'],
      ['Sold', '2 Nov, invoice INV-1042 to Nimal Perera, 077 123 4567'],
      ['Warranty active', 'Until 2 Nov 2027'],
      ['Repairs', 'None'],
    ],
  },
  '351234567890123': {
    device: 'Galaxy S23 256GB',
    events: [
      ['Received', '3 Mar, from Singer Distributors'],
      ['Sold', '14 Nov 2025, invoice INV-0611 to Sunil Traders'],
      ['Warranty claim', 'Pending, battery swelling'],
      ['Repairs', 'None'],
    ],
  },
}

export const SEARCH_INDEX = [
  ['356938035643809', 'Nimal Perera', '077 123 4567', 'INV-1042', 'iPhone 15 128GB Black'],
  ['351234567890123', 'Sunil Traders', '071 900 1234', 'INV-0611', 'Galaxy S23 256GB'],
]

export const POS_PRODUCTS = [
  ['iPhone 15 128GB', 289000, true, 'Phones'],
  ['Galaxy S24 256GB', 329000, true, 'Phones'],
  ['Redmi Note 13', 74500, true, 'Phones'],
  ['MacBook Air M2', 415000, true, 'Laptops'],
  ['Anker 20W charger', 4500, false, 'Accessories'],
  ['Clear case', 2500, false, 'Accessories'],
  ['Screen protector', 1800, false, 'Accessories'],
  ['USB-C cable', 1500, false, 'Accessories'],
]

export function canAccess(role, groupId, itemId, roleDefinitions = ROLES) {
  const permissions = roleDefinitions[role]
  if (!permissions) return false
  if (itemId === 'home') return permissions.home
  if (itemId === 'reports') return permissions.reports
  if (itemId === 'users' && role !== 'Owner') return false
  return permissions.groups.includes(groupId) || permissions.items.includes(itemId)
}

export function getFirstPage(role, roleDefinitions = ROLES) {
  const permissions = roleDefinitions[role]
  if (!permissions) return 'home'
  if (permissions.home) return 'home'
  if (permissions.reports) return 'reports'
  for (const group of NAV_GROUPS) {
    const item = group.items.find(([itemId]) => canAccess(role, group.id, itemId, roleDefinitions))
    if (item) return item[0]
  }
  return 'home'
}

export function getGroupForPage(page) {
  return NAV_GROUPS.find((group) => group.items.some(([itemId]) => itemId === page))?.id ?? ''
}

export function formatRupees(amount) {
  return `Rs ${Math.round(amount).toLocaleString('en-US')}`
}

export function statusClass(status) {
  if (/^(Paid|Received|Ready|Active|In stock|Done)$/.test(status)) return 'c-ok'
  if (/^(Partial|In repair|Pending|Low|Waiting|In transit)$/.test(status)) return 'c-warn'
  if (/^(Overdue|Expired|Out of stock|Unpaid)$/.test(status)) return 'c-bad'
  return 'c-n'
}
