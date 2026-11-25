import { formatRupees } from '../data'

export function Dialog({ dialog, onChange, onClose, onAction }) {
  if (!dialog) return null

  const isDiscount = dialog.kind === 'discount'
  const isInfo = dialog.kind === 'info'
  const isRefund = dialog.kind === 'refund'
  const title = isInfo ? dialog.title : isDiscount ? 'Manager approval' : `Refund ${dialog.reference}?`
  const message = isInfo
    ? dialog.message
    : isDiscount
      ? 'Discounts above 5% need a manager PIN. Demo PIN: 1234'
      : `Enter the amount to refund. Remaining refundable: ${formatRupees(dialog.remaining)}. Optionally enter quantities physically returned to stock.`

  const handleApprove = () => {
    if (onAction() !== false) onClose()
  }

  return (
    <div className="modal" role="presentation" onMouseDown={(event) => {
      if (event.target === event.currentTarget) onClose()
    }}>
      <section className={`modal-box${isRefund ? ' refund-modal-box' : ''}`} role="dialog" aria-modal="true" aria-labelledby="modal-title">
        <h2 id="modal-title">{title}</h2>
        <p>{message}</p>
        {isDiscount && (
          <div className="form-fields">
            <input
              type="password"
              inputMode="numeric"
              placeholder="PIN"
              aria-label="Manager PIN"
              value={dialog.pin}
              onChange={(event) => onChange({ ...dialog, pin: event.target.value })}
              autoFocus
            />
          </div>
        )}
        {isRefund && (
          <div className="form-fields">
            <label htmlFor="refund-amount">Refund amount (Rs)</label>
            <input
              id="refund-amount"
              type="number"
              min="0.01"
              max={dialog.remaining}
              step="0.01"
              value={dialog.amount}
              onChange={(event) => onChange({ ...dialog, amount: event.target.value })}
              autoFocus
            />
            {dialog.returnItems?.length > 0 && (
              <div className="refund-items">
                <b>Items returned to stock</b>
                {dialog.returnItems.map((item, index) => (
                  <label key={`${item.name}-${index}`}>
                    {item.name} (sold: {item.quantity})
                    <input
                      type="number"
                      min="0"
                      max={item.quantity}
                      step="1"
                      value={item.returnedQuantity}
                      onChange={(event) => onChange({
                        ...dialog,
                        returnItems: dialog.returnItems.map((current, itemIndex) =>
                          itemIndex === index
                            ? { ...current, returnedQuantity: event.target.value }
                            : current,
                        ),
                      })}
                      aria-label={`Quantity of ${item.name} returned to stock`}
                    />
                  </label>
                ))}
              </div>
            )}
          </div>
        )}
        <div className="actions">
          {!isInfo && <button className="button button-secondary" type="button" onClick={onClose}>Cancel</button>}
          <button
            className={`button ${isInfo || isDiscount ? 'button-primary' : 'button-danger'}`}
            type="button"
            onClick={handleApprove}
          >
            {isInfo ? 'Got it' : isDiscount ? 'Approve' : 'Record refund'}
          </button>
        </div>
      </section>
    </div>
  )
}
