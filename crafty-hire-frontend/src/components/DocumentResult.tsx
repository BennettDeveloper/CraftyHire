import { useState, useEffect, useMemo, useCallback } from 'react';
import type { GenerateDocumentResponse, ExportFormat, DocumentType } from '../types';
import { exportDocument } from '../api/export';
import { ApiError } from '../api/client';
import ResumeEditor from './ResumeEditor';
import ResumePreview from './ResumePreview';

interface DocumentResultProps {
  resume: GenerateDocumentResponse | null;
  coverLetter: GenerateDocumentResponse | null;
  onRegenerate: () => void;
  generating: boolean;
}

function stripCodeFence(content: string): string {
  return content.replace(/^```[a-z]*\n?/i, '').replace(/\n?```\s*$/, '').trim();
}

export default function DocumentResult({
  resume,
  coverLetter,
  onRegenerate,
  generating,
}: DocumentResultProps) {
  const [activeTab,     setActiveTab]     = useState<DocumentType>(resume ? 'RESUME' : 'COVER_LETTER');
  const [downloadError, setDownloadError] = useState<string | null>(null);
  const [downloading,   setDownloading]   = useState<ExportFormat | null>(null);

  // ── Editor / live-sync state ───────────────────────────────────────────────
  const [editorOpen,   setEditorOpen]   = useState(false);
  const [editorDirty,  setEditorDirty]  = useState(false);
  const [parseError,   setParseError]   = useState<string | null>(null);

  // committedMap: last explicitly saved state (or initial parsed map once available)
  const [committedMap, setCommittedMap] = useState<Record<string, string> | null>(null);
  // liveMap: real-time editor values (null when editor is closed)
  const [liveMap,      setLiveMap]      = useState<Record<string, string> | null>(null);

  // Parse the raw resume content once
  const resumeRawContent = resume?.content ?? '';
  const resumeStripped   = stripCodeFence(resumeRawContent);
  const resumeIsJsonMap  = resumeStripped.startsWith('{');

  const initialParsedMap = useMemo((): Record<string, string> | null => {
    if (!resumeIsJsonMap) return null;
    try { return JSON.parse(resumeStripped) as Record<string, string>; }
    catch { return null; }
  }, [resumeStripped, resumeIsJsonMap]);

  // When a new resume arrives, seed committedMap from the parsed content and reset editor
  useEffect(() => {
    setEditorOpen(false);
    setEditorDirty(false);
    setLiveMap(null);
    setParseError(null);
    setCommittedMap(initialParsedMap);
  }, [resume]); // eslint-disable-line react-hooks/exhaustive-deps

  const active = activeTab === 'RESUME' ? resume : coverLetter;

  // The map shown in the preview:
  // - When editor is open: liveMap (real-time) falling back to committedMap
  // - When editor is closed: committedMap falling back to the initial parsed map
  const previewMap = editorOpen
    ? (liveMap ?? committedMap ?? initialParsedMap)
    : (committedMap ?? initialParsedMap);

  // ── Editor handlers ────────────────────────────────────────────────────────

  function handleOpenEditor() {
    if (!resumeIsJsonMap) return;
    if (!committedMap && !initialParsedMap) {
      setParseError('Could not parse resume data for editing.');
      return;
    }
    setParseError(null);
    setLiveMap(committedMap ?? initialParsedMap);
    setEditorOpen(true);
  }

  function handleCloseEditor() {
    setEditorOpen(false);
    setLiveMap(null);
  }

  // Called by editor on every keystroke / reorder / add / delete
  const handleLiveUpdate = useCallback((map: Record<string, string>) => {
    setLiveMap(map);
  }, []);

  // Called when user clicks "Save Changes" in the editor
  const handleSave = useCallback((map: Record<string, string>) => {
    setCommittedMap(map);
    setEditorDirty(false);
  }, []);

  function handleRegenerate() {
    if (editorOpen && editorDirty) {
      const confirmed = window.confirm('You have unsaved edits. Regenerating will discard them. Continue?');
      if (!confirmed) return;
    }
    setEditorOpen(false);
    setEditorDirty(false);
    setLiveMap(null);
    onRegenerate();
  }

  // ── Download ───────────────────────────────────────────────────────────────
  // Uses committedMap (for resume JSON) so saved edits are reflected in downloads

  async function handleDownload(format: ExportFormat) {
    if (!active) return;
    setDownloadError(null);
    setDownloading(format);
    try {
      // If resume has been edited, use committedMap; otherwise use raw content
      const content = (activeTab === 'RESUME' && committedMap)
        ? JSON.stringify(committedMap)
        : stripCodeFence(active.content);
      await exportDocument(content, format, activeTab);
    } catch (e) {
      setDownloadError(e instanceof ApiError ? e.message : 'Download failed.');
    } finally {
      setDownloading(null);
    }
  }

  // ── Render ─────────────────────────────────────────────────────────────────

  return (
    <>
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
            {/* Resume JSON map → formatted preview; everything else → raw textarea */}
            {activeTab === 'RESUME' && previewMap ? (
              <div className={`resume-preview-box${editorOpen && editorDirty ? ' resume-preview-box--unsaved' : ''}`}>
                {editorOpen && editorDirty && (
                  <div className="preview-unsaved-badge" aria-live="polite">&#8226; Unsaved changes</div>
                )}
                <ResumePreview map={previewMap} />
              </div>
            ) : (
              <textarea
                className="textarea textarea--preview"
                readOnly
                value={active.content}
                rows={14}
              />
            )}

            {downloadError && <p className="field-error">{downloadError}</p>}
            {parseError    && <p className="field-error">{parseError}</p>}

            <div className="export-row">
              {(activeTab === 'RESUME'
                ? (['DOCX', 'LATEX'] as ExportFormat[])
                : (['DOCX']          as ExportFormat[])
              ).map(fmt => (
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

            {/* Edit Resume button — only for DOCX JSON-map resumes */}
            {activeTab === 'RESUME' && resumeIsJsonMap && (
              <button
                className={`btn btn--full${editorOpen ? ' btn--edit-active' : ' btn--secondary'}`}
                onClick={editorOpen ? handleCloseEditor : handleOpenEditor}
                style={{ marginTop: '0.75rem' }}
              >
                {editorOpen ? (
                  <>
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" style={{ marginRight: '0.4em' }}>
                      <polyline points="18 15 12 9 6 15" />
                    </svg>
                    Close Editor
                  </>
                ) : (
                  <>
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" style={{ marginRight: '0.4em' }}>
                      <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
                      <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
                    </svg>
                    Edit Resume
                    {editorDirty && !editorOpen && <span className="edit-dirty-dot" aria-label="Unsaved edits" />}
                  </>
                )}
              </button>
            )}

            <button
              className="btn btn--ghost btn--full"
              onClick={handleRegenerate}
              disabled={generating}
              style={{ marginTop: '0.75rem' }}
            >
              {generating ? 'Regenerating...' : `Regenerate ${activeTab === 'RESUME' ? 'Resume' : 'Cover Letter'}`}
            </button>
          </>
        )}
      </div>

      {/* Inline editor — renders below the results card */}
      {editorOpen && (committedMap ?? initialParsedMap) && (
        <ResumeEditor
          initialMap={(committedMap ?? initialParsedMap)!}
          onLiveUpdate={handleLiveUpdate}
          onSave={handleSave}
          onClose={handleCloseEditor}
          onDirtyChange={setEditorDirty}
        />
      )}
    </>
  );
}
