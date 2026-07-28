import React, { useState, useEffect } from 'react';
import { Modal, Button, TextInput, Checkbox, Alert, Spinner, Select } from '../design-system';
import { api } from '../api.js';

const CHANNELS = ['WEB', 'MOBILE_APP', 'BRANCH', 'PHONE'];

/**
 * UC-06: Create Product Version — add a new version of product configuration.
 *
 * @param {string} productCode - the product to create a version for
 * @param {function} onClose - callback to close modal
 * @param {function} onSuccess - callback after successful creation
 */
export default function NewProductVersionModal({ productCode, onClose, onSuccess }) {
  const [minAge, setMinAge] = useState('18');
  const [limitMin, setLimitMin] = useState('500');
  const [limitMax, setLimitMax] = useState('5000');
  const [selectedChannels, setSelectedChannels] = useState(['WEB']);
  const [active, setActive] = useState(true);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const isValid =
    minAge.trim() &&
    limitMin.trim() &&
    limitMax.trim() &&
    selectedChannels.length > 0 &&
    parseInt(limitMin) < parseInt(limitMax);

  async function handleCreate() {
    if (!isValid) return;

    setLoading(true);
    setError(null);

    try {
      await api.createProductVersion({
        productCode,
        minAge: parseInt(minAge),
        limitMin: parseInt(limitMin),
        limitMax: parseInt(limitMax),
        channels: selectedChannels,
        active,
      });
      onSuccess?.();
      onClose();
    } catch (e) {
      setError(e.message || 'Failed to create version');
    } finally {
      setLoading(false);
    }
  }

  function toggleChannel(channel) {
    setSelectedChannels((prev) =>
      prev.includes(channel) ? prev.filter((c) => c !== channel) : [...prev, channel]
    );
  }

  return (
    <Modal
      open={true}
      title={`New version — ${productCode}`}
      onClose={onClose}
      footer={
        <>
          <Button variant="primary" disabled={!isValid || loading} onClick={handleCreate}>
            {loading ? <Spinner /> : 'Save'}
          </Button>
          <Button variant="secondary" disabled={loading} onClick={onClose}>
            Cancel
          </Button>
        </>
      }
    >
      {error && <Alert tone="negative" title="Creation failed">{error}</Alert>}

      <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
        {/* Min Age */}
        <div>
          <label htmlFor="new-version-minAge" style={{ display: 'block', marginBottom: '0.5rem', fontWeight: 500 }}>
            Minimum Age
          </label>
          <TextInput
            id="new-version-minAge"
            type="number"
            min="18"
            value={minAge}
            onChange={(e) => setMinAge(e.target.value)}
            disabled={loading}
            placeholder="18"
          />
        </div>

        {/* Limit Range */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
          <div>
            <label htmlFor="new-version-limitMin" style={{ display: 'block', marginBottom: '0.5rem', fontWeight: 500 }}>
              Credit Limit Min (GBP)
            </label>
            <TextInput
              id="new-version-limitMin"
              type="number"
              min="0"
              value={limitMin}
              onChange={(e) => setLimitMin(e.target.value)}
              disabled={loading}
              placeholder="500"
            />
          </div>
          <div>
            <label htmlFor="new-version-limitMax" style={{ display: 'block', marginBottom: '0.5rem', fontWeight: 500 }}>
              Credit Limit Max (GBP)
            </label>
            <TextInput
              id="new-version-limitMax"
              type="number"
              min="0"
              value={limitMax}
              onChange={(e) => setLimitMax(e.target.value)}
              disabled={loading}
              placeholder="5000"
            />
          </div>
        </div>

        {/* Channels */}
        <div>
          <label style={{ display: 'block', marginBottom: '0.75rem', fontWeight: 500 }}>
            Channels
          </label>
          <div style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap' }}>
            {CHANNELS.map((channel) => (
              <label key={channel} style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', cursor: 'pointer' }}>
                <Checkbox
                  checked={selectedChannels.includes(channel)}
                  onChange={() => toggleChannel(channel)}
                  disabled={loading}
                />
                <span style={{ fontSize: '0.9375rem' }}>{channel}</span>
              </label>
            ))}
          </div>
        </div>

        {/* Active */}
        <label style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', cursor: 'pointer' }}>
          <Checkbox
            checked={active}
            onChange={(e) => setActive(e.target.checked)}
            disabled={loading}
          />
          <span style={{ fontSize: '0.9375rem', fontWeight: 500 }}>Active</span>
        </label>
      </div>
    </Modal>
  );
}
