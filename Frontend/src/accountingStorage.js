const STORAGE_KEY = 'saas2-accounting-entries'

export function readAccountingEntries() {
  let serialized
  try {
    serialized = window.localStorage.getItem(STORAGE_KEY)
  } catch {
    throw new Error('Browser storage is unavailable.')
  }

  if (!serialized) return []

  let entries
  try {
    entries = JSON.parse(serialized)
  } catch {
    throw new Error('Saved accounting entries could not be read.')
  }

  if (
    !Array.isArray(entries) ||
    !entries.every((entry) =>
      entry &&
      typeof entry.date === 'string' &&
      typeof entry.description === 'string' &&
      typeof entry.debitAccount === 'string' &&
      typeof entry.creditAccount === 'string' &&
      typeof entry.amount === 'number' &&
      typeof entry.branch === 'string',
    )
  ) {
    throw new Error('Saved accounting entries have an invalid format.')
  }

  return entries
}

export function saveAccountingEntries(detailsList) {
  if (detailsList.some((details) =>
    !details ||
    !details.date ||
    !details.description ||
    !details.branch ||
    !details.debitAccount ||
    !details.creditAccount ||
    details.debitAccount === details.creditAccount ||
    !Number.isFinite(details.amount) ||
    details.amount <= 0,
  )) {
    throw new Error('Accounting entry must have a date, description, branch, different accounts, and a positive amount.')
  }

  const entries = readAccountingEntries()
  const knownSourceIds = new Set(entries.map((entry) => entry.sourceId).filter(Boolean))
  const addedEntries = []
  for (const details of detailsList) {
    if (details.sourceId && knownSourceIds.has(details.sourceId)) continue
    const entry = {
      ...details,
      id: `entry-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      createdAt: new Date().toISOString(),
    }
    if (details.sourceId) knownSourceIds.add(details.sourceId)
    addedEntries.push(entry)
  }

  if (addedEntries.length) {
    try {
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify([...addedEntries, ...entries]))
    } catch {
      throw new Error('Accounting entry could not be saved to browser storage.')
    }
  }

  return addedEntries
}

export function saveAccountingEntry(details) {
  const [entry] = saveAccountingEntries([details])
  return entry
}
