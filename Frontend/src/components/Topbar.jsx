import { GlobalSearch } from './GlobalSearch'

export function Topbar({ branch, onBranchChange, search, searchResults, onSearchChange, onSearchSelect, onMenuToggle, onNotify, role }) {
  return (
    <header className="topbar">
      <button
        className="menu-button"
        type="button"
        aria-label="Toggle menu"
        onClick={onMenuToggle}
      >
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M4 6h16M4 12h16M4 18h16" />
        </svg>
      </button>
      <GlobalSearch
        value={search}
        results={searchResults}
        onChange={onSearchChange}
        onSelect={onSearchSelect}
      />
      <label className="branch-control">
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M20 10c0 5-8 12-8 12S4 15 4 10a8 8 0 1 1 16 0Z" />
          <circle cx="12" cy="10" r="2.5" />
        </svg>
        <select value={branch} onChange={(event) => onBranchChange(event.target.value)} aria-label="Branch">
          {['Colombo 03', 'Kandy', 'Galle'].map((branchName) => <option key={branchName}>{branchName}</option>)}
        </select>
        <span className="branch-chevron">⌄</span>
      </label>
      <button className="notification-button" type="button" aria-label="Notifications" onClick={onNotify}>
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M18 9a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4" />
        </svg>
        <span className="notification-dot" />
      </button>
      <div className="top-avatar" title={`Signed in as ${role}`}>{role.slice(0, 1)}</div>
    </header>
  )
}
