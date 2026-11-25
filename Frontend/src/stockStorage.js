const STORAGE_KEY = 'saas2-stock-received-devices'

export function readReceivedDevices() {
  let serialized
  try {
    serialized = window.localStorage.getItem(STORAGE_KEY)
  } catch {
    throw new Error('Browser storage is unavailable.')
  }

  if (!serialized) return []

  let devices
  try {
    devices = JSON.parse(serialized)
  } catch {
    throw new Error('Saved received devices could not be read.')
  }

  if (
    !Array.isArray(devices) ||
    !devices.every((device) =>
      device &&
      typeof device.productCode === 'string' &&
      typeof device.imei === 'string' &&
      typeof device.branch === 'string' &&
      typeof device.supplier === 'string' &&
      typeof device.purchaseOrder === 'string' &&
      typeof device.receivedAt === 'string',
    )
  ) {
    throw new Error('Saved received devices have an invalid format.')
  }

  return devices
}

export function saveStockReceipt(receipt) {
  const devices = readReceivedDevices()
  const existingImeis = new Set(devices.map((device) => device.imei))
  for (const device of receipt.devices) {
    if (existingImeis.has(device.imei)) {
      throw new Error(`IMEI ${device.imei} has already been received.`)
    }
    existingImeis.add(device.imei)
  }

  const receivedAt = new Date().toISOString()
  const receiptId = `receipt-${Date.now()}`
  const addedDevices = receipt.devices.map((device) => ({
    ...device,
    category: receipt.category,
    productName: receipt.productName,
    quantity: receipt.quantity,
    totalBuyingPrice: receipt.totalBuyingPrice,
    buyingPricePerItem: receipt.buyingPricePerItem,
    sellingPricePerItem: receipt.sellingPricePerItem,
    branch: receipt.branch,
    supplier: receipt.supplier,
    purchaseOrder: receipt.purchaseOrder,
    receivedAt,
    receiptId,
    status: 'In stock',
  }))

  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify([...devices, ...addedDevices]))
  } catch {
    throw new Error('Received devices could not be saved to browser storage.')
  }

  return addedDevices
}

export function recordStockSale(sale, branch) {
  const devices = readReceivedDevices()
  const updatedDevices = [...devices]
  let trackedQuantity = 0
  let updatedQuantity = 0
  let costedQuantity = 0
  let costOfGoodsSold = 0
  let untrackedQuantity = 0
  const processedDevices = new Set()

  sale.items.forEach((line) => {
    const quantity = Math.max(0, Number(line.quantity) || 0)
    for (let unit = 0; unit < quantity; unit += 1) {
      const matchesLine = (device) => line.imeiRequired
        ? device.imei === line.imei
        : device.productName === line.name
      const previouslySoldIndex = updatedDevices.findIndex((device, index) =>
        !processedDevices.has(index) &&
        device.status === 'Sold' &&
        device.saleInvoice === sale.invoice &&
        device.branch === branch &&
        matchesLine(device),
      )
      if (previouslySoldIndex >= 0) {
        processedDevices.add(previouslySoldIndex)
        trackedQuantity += 1
        const soldDevice = updatedDevices[previouslySoldIndex]
        if (Number.isFinite(soldDevice.buyingPricePerItem) && soldDevice.buyingPricePerItem > 0) {
          costedQuantity += 1
          costOfGoodsSold += soldDevice.buyingPricePerItem
        }
        continue
      }

      const deviceIndex = updatedDevices.findIndex((device, index) =>
        !processedDevices.has(index) &&
        device.branch === branch &&
        device.status !== 'Sold' &&
        !(device.saleInvoice === sale.invoice && device.refundId) &&
        matchesLine(device),
      )
      if (deviceIndex < 0) {
        untrackedQuantity += 1
        continue
      }

      const device = updatedDevices[deviceIndex]
      updatedDevices[deviceIndex] = {
        ...device,
        status: 'Sold',
        soldAt: sale.createdAt,
        saleInvoice: sale.invoice,
        refundId: undefined,
        returnedAt: undefined,
      }
      processedDevices.add(deviceIndex)
      trackedQuantity += 1
      updatedQuantity += 1
      if (Number.isFinite(device.buyingPricePerItem) && device.buyingPricePerItem > 0) {
        costedQuantity += 1
        costOfGoodsSold += device.buyingPricePerItem
      }
    }

  })

  if (updatedQuantity) {
    try {
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify(updatedDevices))
    } catch {
      throw new Error('Sold inventory quantities could not be saved.')
    }
  }

  return {
    devices: updatedDevices,
    trackedQuantity,
    updatedQuantity,
    costedQuantity,
    costOfGoodsSold,
    untrackedQuantity,
  }
}

export function recordStockRefund(sale, returnItems, branch, refundId) {
  const devices = readReceivedDevices()
  const updatedDevices = [...devices]
  let returnedQuantity = 0
  let updatedQuantity = 0
  let costOfGoodsReturned = 0
  const processedDevices = new Set()

  for (const item of returnItems) {
    const quantity = Number(item.returnedQuantity)
    if (!Number.isInteger(quantity) || quantity < 0 || quantity > item.quantity) {
      throw new Error(`Invalid return quantity for ${item.name}.`)
    }

    for (let unit = 0; unit < quantity; unit += 1) {
      const matchesItem = (device) => item.imeiRequired
        ? device.imei === item.imei
        : device.productName === item.name
      const previouslyReturnedIndex = updatedDevices.findIndex((device, index) =>
        !processedDevices.has(index) &&
        device.status === 'In stock' &&
        device.saleInvoice === sale.invoice &&
        device.refundId === refundId &&
        device.branch === branch &&
        matchesItem(device),
      )
      if (previouslyReturnedIndex >= 0) {
        processedDevices.add(previouslyReturnedIndex)
        returnedQuantity += 1
        const returnedDevice = updatedDevices[previouslyReturnedIndex]
        if (Number.isFinite(returnedDevice.buyingPricePerItem)) {
          costOfGoodsReturned += returnedDevice.buyingPricePerItem
        }
        continue
      }

      const deviceIndex = updatedDevices.findIndex((device, index) =>
        !processedDevices.has(index) &&
        device.status === 'Sold' &&
        device.saleInvoice === sale.invoice &&
        device.branch === branch &&
        matchesItem(device),
      )
      if (deviceIndex < 0) {
        throw new Error(`No sold inventory record was found for ${item.name}.`)
      }

      const device = updatedDevices[deviceIndex]
      updatedDevices[deviceIndex] = {
        ...device,
        status: 'In stock',
        refundId,
        returnedAt: new Date().toISOString(),
      }
      processedDevices.add(deviceIndex)
      returnedQuantity += 1
      updatedQuantity += 1
      if (Number.isFinite(device.buyingPricePerItem)) {
        costOfGoodsReturned += device.buyingPricePerItem
      }
    }
  }

  if (updatedQuantity) {
    try {
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify(updatedDevices))
    } catch {
      throw new Error('Returned inventory quantities could not be saved.')
    }
  }

  return { devices: updatedDevices, returnedQuantity, updatedQuantity, costOfGoodsReturned }
}
