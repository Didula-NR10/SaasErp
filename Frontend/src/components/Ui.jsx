export function Button({ children, variant = 'primary', icon, className = '', ...props }) {
  return (
    <button className={`button button-${variant}${className ? ` ${className}` : ''}`} {...props}>
      {icon && <span className="button-icon" aria-hidden="true">{icon}</span>}
      {children}
    </button>
  )
}

export function PageHeader({ title, breadcrumbs = ['Dashboard', title], action, context }) {
  return (
    <div className="page-header">
      <div>
        <h1>{title}</h1>
        <nav className="breadcrumbs" aria-label="Breadcrumb">
          {breadcrumbs.map((crumb, index) => (
            <span className={index === breadcrumbs.length - 1 ? 'current' : ''} key={`${crumb}-${index}`}>
              {index > 0 && <span className="breadcrumb-separator" aria-hidden="true">›</span>}
              {crumb}
            </span>
          ))}
        </nav>
        {context && <p className="page-context">{context}</p>}
      </div>
      {action && <div className="page-header-action">{action}</div>}
    </div>
  )
}

export function FilterBar({ children }) {
  return <div className="filter-bar">{children}</div>
}

export function DataTable({ columns, children, className = '' }) {
  return (
    <div className={`data-table-card${className ? ` ${className}` : ''}`}>
      <div className="table-scroll">
        <table className="data-table">
          <thead>
            <tr>{columns.map((column, index) => <th key={`${column}-${index}`}>{column}</th>)}</tr>
          </thead>
          <tbody>{children}</tbody>
        </table>
      </div>
    </div>
  )
}

function AccountingIllustration() {
  return (
    <svg className="empty-illustration" viewBox="0 0 520 230" role="img" aria-label="Calculator, receipts, and coins">
      <defs>
        <linearGradient id="paper" x1="0" x2="1" y1="0" y2="1">
          <stop offset="0" stopColor="#fff" />
          <stop offset="1" stopColor="#e9ebef" />
        </linearGradient>
        <linearGradient id="gold" x1="0" x2="0.9" y1="0" y2="1">
          <stop offset="0" stopColor="#ffc34f" />
          <stop offset="1" stopColor="#e88800" />
        </linearGradient>
        <linearGradient id="device" x1="0" x2="1" y1="0" y2="1">
          <stop offset="0" stopColor="#42464e" />
          <stop offset="1" stopColor="#111318" />
        </linearGradient>
        <filter id="soft-shadow" x="-30%" y="-30%" width="160%" height="180%">
          <feGaussianBlur in="SourceAlpha" stdDeviation="7" />
          <feOffset dy="8" />
          <feComponentTransfer><feFuncA type="linear" slope=".2" /></feComponentTransfer>
          <feMerge><feMergeNode /><feMergeNode in="SourceGraphic" /></feMerge>
        </filter>
      </defs>
      <ellipse cx="260" cy="193" rx="204" ry="17" fill="#e9e9eb" opacity=".7" />
      <ellipse cx="260" cy="189" rx="190" ry="26" fill="none" stroke="#f6c577" opacity=".55" />
      <g filter="url(#soft-shadow)">
        <path d="m191 34 85 5-9 125-88-5z" fill="url(#paper)" />
        <path d="m183 54 56 3m-58 18 42 2m-43 18 52 3m-54 18 38 2m-39 17 51 3" stroke="#afb2b8" strokeWidth="7" strokeLinecap="round" />
        <path d="m301 56 67 18-21 99-69-17z" fill="url(#paper)" />
        <rect x="304" y="106" width="13" height="32" rx="3" fill="#c6c8cd" transform="rotate(13 304 106)" />
        <rect x="326" y="94" width="14" height="43" rx="3" fill="#b9bbc1" transform="rotate(13 326 94)" />
        <g transform="rotate(12 270 133)">
          <rect x="229" y="68" width="91" height="122" rx="15" fill="url(#device)" />
          <rect x="240" y="79" width="69" height="17" rx="4" fill="#080a0d" />
          {Array.from({ length: 9 }, (_, index) => {
            const x = 242 + (index % 3) * 20
            const y = 106 + Math.floor(index / 3) * 18
            return <rect key={index} x={x} y={y} width="13" height="9" rx="2" fill="#686c73" />
          })}
          <rect x="282" y="160" width="14" height="17" rx="3" fill="url(#gold)" />
        </g>
        <ellipse cx="150" cy="165" rx="27" ry="8" fill="url(#gold)" />
        <rect x="123" y="151" width="54" height="14" fill="url(#gold)" />
        <ellipse cx="150" cy="151" rx="27" ry="8" fill="#ffc34f" />
        <ellipse cx="151" cy="140" rx="20" ry="7" fill="url(#gold)" />
        <rect x="131" y="128" width="40" height="12" fill="url(#gold)" />
        <ellipse cx="151" cy="128" rx="20" ry="7" fill="#ffc34f" />
        <ellipse cx="198" cy="166" rx="24" ry="7" fill="url(#gold)" />
        <rect x="174" y="155" width="48" height="11" fill="url(#gold)" />
        <ellipse cx="198" cy="155" rx="24" ry="7" fill="#ffc34f" />
      </g>
      <rect x="91" y="83" width="20" height="20" rx="5" fill="#ffb132" transform="rotate(8 91 83)" />
    </svg>
  )
}

export function EmptyState({ title = 'No Records Yet', description, actionLabel, onAction, illustration = false }) {
  return (
    <div className="empty-state">
      {illustration && <AccountingIllustration />}
      <h2>{title}</h2>
      {description && <p>{description}</p>}
      {actionLabel && onAction && (
        <Button onClick={onAction} icon="+">{actionLabel}<span className="button-arrow">›</span></Button>
      )}
    </div>
  )
}
