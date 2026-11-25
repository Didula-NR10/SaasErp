import { NAV_GROUPS, ROLES, canAccess } from '../data'

export function Sidebar({
  role,
  roles = ROLES,
  page,
  openGroup,
  menuOpen,
  onToggleMenu,
  onToggleGroup,
  onNavigate,
  onRoleChange,
}) {
  const icons = {
    home: <><path d="m3 10 9-7 9 7" /><path d="M5 9v11h14V9M9 20v-6h6v6" /></>,
    reports: <><path d="M4 20V4M4 20h16" /><path d="M8 16v-5M12 16V7M16 16v-3" /></>,
    sell: <><path d="M3 4h2l2.2 11.2a2 2 0 0 0 2 1.6h8.6a2 2 0 0 0 1.9-1.4L22 8H6" /><circle cx="10" cy="21" r="1" /><circle cx="18" cy="21" r="1" /></>,
    stock: <><path d="m12 3 9 5-9 5-9-5 9-5Z" /><path d="M3 8v9l9 5 9-5V8M12 13v9" /></>,
    buy: <><path d="M4 5h2l2 11h11l2-8H7" /><circle cx="10" cy="20" r="1" /><circle cx="18" cy="20" r="1" /><path d="M11 11h6M14 8v6" /></>,
    after: <><path d="M4 13v-2a8 8 0 0 1 16 0v2" /><rect x="3" y="12" width="4" height="7" rx="2" /><rect x="17" y="12" width="4" height="7" rx="2" /><path d="M17 20h-5" /></>,
    money: <><rect x="3" y="5" width="18" height="14" rx="2" /><path d="M3 9h18M16 14h2" /></>,
    admin: <><circle cx="9" cy="8" r="3" /><path d="M3 20v-1a6 6 0 0 1 12 0v1M16 5a3 3 0 0 1 0 6M18 14a5 5 0 0 1 3 5v1" /></>,
  }

  return (
    <>
      {menuOpen && (
        <button
          className="side-backdrop"
          type="button"
          aria-label="Close menu"
          onClick={onToggleMenu}
        />
      )}
      <nav className={`side${menuOpen ? ' show' : ''}`} aria-label="Main menu">
        <div className="brand" aria-label="Hi.Ch">
          <svg className="brand-mark" viewBox="0 0 44 40" aria-hidden="true">
            <path d="M3 7h8v9h12v-9h8v26h-8v-10H11v10H3z" fill="currentColor" />
            <circle cx="33" cy="8" r="6" fill="var(--brand-orange)" />
            <circle cx="33" cy="32" r="6" fill="var(--brand-orange)" />
          </svg>
          <span>hi.ch</span>
        </div>
        {canAccess(role, '', 'home', roles) && (
          <button className={`nav${page === 'home' ? ' on' : ''}`} type="button" onClick={() => onNavigate('home')}>
            <svg viewBox="0 0 24 24" aria-hidden="true">{icons.home}</svg>Home
          </button>
        )}
        {canAccess(role, '', 'reports', roles) && (
          <button className={`nav${page === 'reports' ? ' on' : ''}`} type="button" onClick={() => onNavigate('reports')}>
            <svg viewBox="0 0 24 24" aria-hidden="true">{icons.reports}</svg>Reports
          </button>
        )}
        {NAV_GROUPS.map((group) => {
          const items = group.items.filter(([itemId]) => canAccess(role, group.id, itemId, roles))
          if (!items.length) return null
          const isOpen = openGroup === group.id
          return (
            <div className={`group${isOpen ? ' open' : ''}`} key={group.id}>
              <button
                className="nav"
                type="button"
                aria-expanded={isOpen}
                onClick={() => onToggleGroup(group.id)}
              >
                <svg viewBox="0 0 24 24" aria-hidden="true">{icons[group.id]}</svg>
                {group.name}
                <span className="caret">{isOpen ? '▾' : '▸'}</span>
              </button>
              <div className="sub">
                {items.map(([itemId, itemName]) => (
                  <button
                    className={`nav${page === itemId ? ' on' : ''}`}
                    type="button"
                    key={itemId}
                    onClick={() => onNavigate(itemId)}
                  >
                    {itemName}
                  </button>
                ))}
              </div>
            </div>
          )
        })}
        <div className="sidebar-watermark" aria-hidden="true">
          <svg viewBox="0 0 170 150">
            <path d="M22 30h32v35h47V30h32v99h-32V91H54v38H22z" fill="currentColor" />
            <circle cx="142" cy="31" r="20" fill="var(--brand-orange)" />
            <circle cx="142" cy="127" r="20" fill="var(--brand-orange)" />
          </svg>
        </div>
        <label className="who">
          <span className="online-dot" />
          Signed in as
          <div className="role-select-wrap">
            <span className="profile-avatar">{role.slice(0, 1)}</span>
            <select value={role} onChange={(event) => onRoleChange(event.target.value)}>
              {Object.keys(roles).map((roleName) => <option key={roleName}>{roleName}</option>)}
            </select>
            <span className="role-caret">⌄</span>
          </div>
        </label>
      </nav>
    </>
  )
}
