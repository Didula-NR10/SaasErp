import { NAV_GROUPS, ROLES } from './data'

const STORAGE_KEY = 'saas2-role-definitions'
const groupIds = new Set(NAV_GROUPS.map((group) => group.id))
const pageIds = new Set(NAV_GROUPS.flatMap((group) => group.items.map(([itemId]) => itemId)))

function isRoleDefinition(value) {
  return value
    && Array.isArray(value.groups)
    && value.groups.every((groupId) => groupIds.has(groupId))
    && Array.isArray(value.items)
    && value.items.every((itemId) => pageIds.has(itemId))
    && typeof value.home === 'boolean'
    && typeof value.reports === 'boolean'
}

export function readRoleDefinitions() {
  const stored = localStorage.getItem(STORAGE_KEY)
  if (!stored) return { ...ROLES }

  const parsed = JSON.parse(stored)
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new Error('Saved role permissions are invalid')
  }

  const roleDefinitions = { ...ROLES }
  Object.entries(parsed).forEach(([name, permissions]) => {
    if (name !== 'Owner' && name.trim() && isRoleDefinition(permissions)) {
      roleDefinitions[name] = permissions
    }
  })
  roleDefinitions.Owner = ROLES.Owner
  return roleDefinitions
}

export function saveRoleDefinitions(roleDefinitions) {
  const savedDefinitions = Object.fromEntries(
    Object.entries(roleDefinitions)
      .filter(([name]) => name !== 'Owner')
      .map(([name, permissions]) => {
        if (!name.trim() || !isRoleDefinition(permissions)) {
          throw new Error(`Permissions for "${name}" are invalid`)
        }
        return [name, permissions]
      }),
  )
  localStorage.setItem(STORAGE_KEY, JSON.stringify(savedDefinitions))
  return { ...roleDefinitions, Owner: ROLES.Owner }
}
