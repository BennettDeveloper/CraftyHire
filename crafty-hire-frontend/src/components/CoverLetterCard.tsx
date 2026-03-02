import { useState } from 'react';

interface CoverLetterCardProps {
  value: string;
  onChange: (text: string) => void;
}

export default function CoverLetterCard({ value, onChange }: CoverLetterCardProps) {
  const [expanded, setExpanded] = useState(false);

  return (
    <div className="card">
      <button
        className="card__header card__header--toggle"
        onClick={() => setExpanded((v) => !v)}
        aria-expanded={expanded}
      >
        {/* Document icon */}
        <svg className="card__icon" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
          <polyline points="14 2 14 8 20 8" />
        </svg>
        <h2 className="card__title">Cover Letter Template <span className="card__optional">(Optional)</span></h2>
        <span className={`card__chevron${expanded ? ' card__chevron--up' : ''}`} aria-hidden="true">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
            <polyline points="6 9 12 15 18 9" />
          </svg>
        </span>
      </button>

      {expanded && (
        <div className="card__body">
          <label className="field-label">
            Paste a previous cover letter to use as a style reference
          </label>
          <textarea
            className="textarea"
            placeholder="Paste your previous cover letter here..."
            value={value}
            onChange={(e) => onChange(e.target.value)}
            rows={8}
          />
        </div>
      )}
    </div>
  );
}
