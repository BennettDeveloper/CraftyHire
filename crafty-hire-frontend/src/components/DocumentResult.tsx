import { useState } from 'react';
import type { GenerateDocumentResponse, ExportFormat, DocumentType } from '../types';
import { exportDocument } from '../api/export';
import { ApiError } from '../api/client';

interface DocumentResultProps {
  resume: GenerateDocumentResponse | null;
  coverLetter: GenerateDocumentResponse | null;
  onRegenerate: (type: DocumentType) => void;
  generating: boolean;
}

export default function DocumentResult({
  resume,
  coverLetter,
  onRegenerate,
  generating,
}: DocumentResultProps) {
  const [activeTab, setActiveTab] = useState<DocumentType>(resume ? 'RESUME' : 'COVER_LETTER');
  const [downloadError, setDownloadError] = useState<string | null>(null);
  const [downloading, setDownloading] = useState<ExportFormat | null>(null);

  const active = activeTab === 'RESUME' ? resume : coverLetter;

  async function handleDownload(format: ExportFormat) {
    if (!active) return;
    setDownloadError(null);
    setDownloading(format);
    try {
      await exportDocument(active.content, format, activeTab);
    } catch (e) {
      setDownloadError(e instanceof ApiError ? e.message : 'Download failed.');
    } finally {
      setDownloading(null);
    }
  }

  return (
    <div className="right-panel">
      <div className="right-panel__header">
        <svg className="right-panel__icon" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
          <polyline points="14 2 14 8 20 8" />
        </svg>
        <h2 className="right-panel__title">Generated Documents</h2>
      </div>

      {/* Tabs */}
      <div className="tabs">
        {resume && (
          <button
            className={`tab${activeTab === 'RESUME' ? ' tab--active' : ''}`}
            onClick={() => setActiveTab('RESUME')}
          >
            Resume
          </button>
        )}
        {coverLetter && (
          <button
            className={`tab${activeTab === 'COVER_LETTER' ? ' tab--active' : ''}`}
            onClick={() => setActiveTab('COVER_LETTER')}
          >
            Cover Letter
          </button>
        )}
      </div>

      {active && (
        <>
          <textarea
            className="textarea textarea--preview"
            readOnly
            value={active.content}
            rows={14}
          />

          {downloadError && <p className="field-error">{downloadError}</p>}

          <div className="export-row">
            {(['PDF', 'DOCX', 'LATEX'] as ExportFormat[]).map((fmt) => (
              <button
                key={fmt}
                className="btn btn--export"
                onClick={() => handleDownload(fmt)}
                disabled={downloading !== null}
              >
                {downloading === fmt ? '...' : `↓ ${fmt}`}
              </button>
            ))}
          </div>

          <button
            className="btn btn--ghost btn--full"
            onClick={() => onRegenerate(activeTab)}
            disabled={generating}
            style={{ marginTop: '0.75rem' }}
          >
            {generating ? 'Regenerating...' : `Regenerate ${activeTab === 'RESUME' ? 'Resume' : 'Cover Letter'}`}
          </button>
        </>
      )}
    </div>
  );
}
