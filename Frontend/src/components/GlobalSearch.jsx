import { IMEI_DATA } from '../data'

export function GlobalSearch({ value, results, onChange, onSelect }) {
  return (
    <div
      className="search-box"
      onBlur={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget)) onChange('')
      }}
    >
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <circle cx="10.8" cy="10.8" r="6.8" />
        <path d="m16 16 5 5" />
      </svg>
      <input
        id="global-search"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder="Search anything... (e.g. invoice, customer, product)"
        autoComplete="off"
        aria-label="Search everything"
        aria-expanded={Boolean(value.trim())}
      />
      <kbd className="search-shortcut">Ctrl + K</kbd>
      {value.trim() && (
        <div className="search-results">
          {results.length ? results.map(([imei, customer, , invoice]) => (
            <button
              type="button"
              key={imei}
              onMouseDown={(event) => event.preventDefault()}
              onClick={() => onSelect(imei)}
            >
              <b>{IMEI_DATA[imei]?.device || `IMEI ${imei}`}</b>
              <small>{customer}, {invoice}, IMEI {imei}</small>
            </button>
          )) : (
            <button type="button" disabled>
              No match. Check the IMEI, phone, or invoice number.
            </button>
          )}
        </div>
      )}
    </div>
  )
}
