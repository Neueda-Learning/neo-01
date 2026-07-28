import React, { useState } from 'react';
import { Modal, Button, Textarea, TextInput, Alert, Spinner } from '../design-system';
import { api } from '../api.js';

/**
 * UC-05: Override decision — allows manually changing a verification outcome.
 *
 * @param {string} applicationId - the case to override
 * @param {string} currentOutcome - current PASSED/FAILED/REVIEW
 * @param {function} onClose - callback to close modal
 * @param {function} onSuccess - callback after successful override
 */
export default function OverrideModal({ applicationId, currentOutcome, onClose, onSuccess }) {
  const [newOutcome, setNewOutcome] = useState(null);
  const [reason, setReason] = useState('');
  const [operator, setOperator] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const isValid = newOutcome && reason.trim() && operator.trim();

  async function handleConfirm() {
    if (!isValid) return;

    setLoading(true);
    setError(null);

    try {
      await api.overrideCase(applicationId, {
        newOutcome,
        reason: reason.trim(),
        operator: operator.trim(),
      });
      onSuccess?.();
      onClose();
    } catch (e) {
      setError(e.message || 'Failed to override decision');
    } finally {
      setLoading(false);
    }
  }

  return (
    <Modal
      open={true}
      title={`Override decision — ${applicationId}`}
      onClose={onClose}
      footer={
        <>
          <Button
            variant="primary"
            disabled={!isValid || loading}
            onClick={handleConfirm}
          >
            {loading ? <Spinner /> : 'Confirm override'}
          </Button>
          <Button variant="secondary" disabled={loading} onClick={onClose}>
            Cancel
          </Button>
        </>
      }
    >
      {error && <Alert tone="negative" title="Override failed">{error}</Alert>}

      <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
        {/* New Outcome Selection */}
        <div>
          <label style={{ display: 'block', marginBottom: '0.75rem', fontWeight: 500 }}>
            NEW OUTCOME
          </label>
          <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap' }}>
            {['PASSED', 'FAILED', 'REVIEW'].map((outcome) => (
              <button
                key={outcome}
                onClick={() => setNewOutcome(outcome)}
                style={{
                  padding: '0.5rem 1rem',
                  border: `2px solid ${newOutcome === outcome ? '#17a697' : '#d4d4d4'}`,
                  borderRadius: '999px',
                  background: newOutcome === outcome ? '#e8f5f3' : 'white',
                  color: newOutcome === outcome ? '#17a697' : '#505050',
                  cursor: 'pointer',
                  fontWeight: newOutcome === outcome ? 600 : 400,
                  fontSize: '0.9375rem',
                  transition: 'all 0.2s ease',
                }}
              >
                {outcome}
              </button>
            ))}
          </div>
        </div>

        {/* Reason Text */}
        <div>
          <label htmlFor="override-reason" style={{ display: 'block', marginBottom: '0.75rem', fontWeight: 500 }}>
            REASON (REQUIRED)
          </label>
          <Textarea
            id="override-reason"
            placeholder="Documents re-checked with branch — DOB typo on the form, applicant is 19. Passing manually."
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            disabled={loading}
          />
        </div>

        {/* Operator */}
        <div>
          <label htmlFor="override-operator" style={{ display: 'block', marginBottom: '0.75rem', fontWeight: 500 }}>
            OPERATOR
          </label>
          <TextInput
            id="override-operator"
            placeholder="b.dimovski"
            value={operator}
            onChange={(e) => setOperator(e.target.value)}
            disabled={loading}
          />
        </div>
      </div>
    </Modal>
  );
}
