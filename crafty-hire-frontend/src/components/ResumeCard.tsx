import { useRef, useState } from 'react';
import { parseResume } from '../api/resume';
import { ApiError } from '../api/client';

interface ResumeCardProps {
  resumeText: string;
  onResumeText: (text: string) => void;
}

export default function ResumeCard({ resumeText, onResumeText }: ResumeCardProps) {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [dragOver, setDragOver] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [uploadedFileName, setUploadedFileName] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function handleFile(file: File) {
    setError(null);
    setUploading(true);
    try {
      const result = await parseResume(file);
      onResumeText(result.resumeText);
      setUploadedFileName(file.name);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Failed to parse file.');
    } finally {
      setUploading(false);
    }
  }

  function onFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (file) handleFile(file);
  }

  function onDrop(e: React.DragEvent) {
    e.preventDefault();
    setDragOver(false);
    const file = e.dataTransfer.files[0];
    if (file) handleFile(file);
  }

  return (
    <div className="card">
      <div className="card__header">
        {/* Document icon */}
        <svg className="card__icon" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
          <polyline points="14 2 14 8 20 8" />
          <line x1="16" y1="13" x2="8" y2="13" />
          <line x1="16" y1="17" x2="8" y2="17" />
          <polyline points="10 9 9 9 8 9" />
        </svg>
        <h2 className="card__title">Resume Template</h2>
      </div>

      {/* File upload drop zone */}
      <div
        className={`upload-zone${dragOver ? ' upload-zone--active' : ''}`}
        onDragOver={(e) => { e.preventDefault(); setDragOver(true); }}
        onDragLeave={() => setDragOver(false)}
        onDrop={onDrop}
        onClick={() => fileInputRef.current?.click()}
      >
        <input
          ref={fileInputRef}
          type="file"
          accept=".pdf,.docx,.tex"
          style={{ display: 'none' }}
          onChange={onFileChange}
        />
        {uploading ? (
          <span className="upload-zone__text">Parsing file...</span>
        ) : uploadedFileName ? (
          <span className="upload-zone__text upload-zone__text--success">
            ✓ {uploadedFileName} — <span className="link">replace file</span>
          </span>
        ) : (
          <span className="upload-zone__text">
            Drop PDF, DOCX, or LaTeX here, or <span className="link">browse</span>
          </span>
        )}
      </div>

      {error && <p className="field-error">{error}</p>}

      <div className="divider">
        <span>or paste your resume text below</span>
      </div>

      <label className="field-label">Paste your current resume below</label>
      <textarea
        className="textarea"
        placeholder="Paste your resume here..."
        value={resumeText}
        onChange={(e) => {
          setUploadedFileName(null);
          onResumeText(e.target.value);
        }}
        rows={8}
      />
    </div>
  );
}
