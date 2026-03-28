import { useState, useMemo, useEffect } from 'react';
import { apiBlob } from '../api/client';
import { ApiError } from '../api/client';

// ─── Schema ───────────────────────────────────────────────────────────────────
// Exact placeholder keys extracted from resume-docx-template.docx.
// classifyKey() maps each raw bracket key to its section, group, label, and
// field ordering so the editor can render a structured human-readable form.

interface FieldMeta {
  section: string;
  sectionOrder: number;
  group: string | null;
  groupIndex: number;
  label: string;
  fieldOrder: number;
  required: boolean;
  multiline: boolean;
}

const HEADER_EXACT: Record<string, { label: string; required?: boolean; order: number }> = {
  '[FIRST AND LAST NAME]':  { label: 'Full Name',    required: true, order: 0 },
  '[Phone Number]':         { label: 'Phone',         required: true, order: 1 },
  '[Professional Email]':   { label: 'Email',         required: true, order: 2 },
  '[LinkedIn URL]':         { label: 'LinkedIn URL',                  order: 3 },
  '[GitHub URL]':           { label: 'GitHub URL',                    order: 4 },
  '[City, State]':          { label: 'City, State',                   order: 5 },
};

const SKILLS_PREFIXES: { prefix: string; label: string; order: number }[] = [
  { prefix: '[Languages:',        label: 'Languages',         order: 0 },
  { prefix: '[Frameworks:',       label: 'Frameworks',        order: 1 },
  { prefix: '[Databases:',        label: 'Databases',         order: 2 },
  { prefix: '[Tools:',            label: 'Tools & Platforms', order: 3 },
  { prefix: '[Methodologies:',    label: 'Methodologies',     order: 4 },
  { prefix: '[Professional Skills:', label: 'Professional Skills', order: 5 },
  { prefix: '[Certifications:',   label: 'Certifications',    order: 6 },
];

function classifyKey(key: string): FieldMeta | null {
  // Skip internal signals that aren't content fields
  if (!key.startsWith('[')) return null;

  // Header — exact match
  if (key in HEADER_EXACT) {
    const def = HEADER_EXACT[key];
    return {
      section: 'Header', sectionOrder: 0,
      group: null, groupIndex: 0,
      label: def.label, fieldOrder: def.order,
      required: def.required ?? false, multiline: false,
    };
  }

  // Professional Summary — startsWith
  if (key.startsWith('[Professional Summary:')) {
    return {
      section: 'Professional Summary', sectionOrder: 1,
      group: null, groupIndex: 0,
      label: 'Summary', fieldOrder: 0,
      required: false, multiline: true,
    };
  }

  // Skills — startsWith
  for (const s of SKILLS_PREFIXES) {
    if (key.startsWith(s.prefix)) {
      return {
        section: 'Skills & Certifications', sectionOrder: 2,
        group: null, groupIndex: 0,
        label: s.label, fieldOrder: s.order,
        required: false, multiline: true,
      };
    }
  }

  let m: RegExpMatchArray | null;

  // ── Experience ──────────────────────────────────────────────────────────────
  m = key.match(/^\[Job Title (\d+)\]$/);
  if (m) return exp(+m[1], 'Job Title', 0, false);

  m = key.match(/^\[Company Name (\d+)\]$/);
  if (m) return exp(+m[1], 'Company', 1, false);

  m = key.match(/^\[City, State (\d+)\]$/);
  if (m) return exp(+m[1], 'City, State', 2, false);

  m = key.match(/^\[Month Year (\d+) Start\]$/);
  if (m) return exp(+m[1], 'Start Date', 3, false);

  m = key.match(/^\[Month Year (\d+) End\]$/);
  if (m) return exp(+m[1], 'End Date', 4, false);

  m = key.match(/^\[Job (\d+) Bullet (\d+):/);
  if (m) return exp(+m[1], `Bullet ${m[2]}`, 10 + +m[2], true);

  // ── Projects ────────────────────────────────────────────────────────────────
  m = key.match(/^\[Project Name (\d+)\]$/);
  if (m) return proj(+m[1], 'Project Name', 0, false);

  m = key.match(/^\[Tech Stack (\d+)\]$/);
  if (m) return proj(+m[1], 'Tech Stack', 1, false);

  m = key.match(/^\[GitHub Link (\d+)\]$/);
  if (m) return proj(+m[1], 'GitHub Link', 2, false);

  m = key.match(/^\[Live Demo Link (\d+)\]$/);
  if (m) return proj(+m[1], 'Live Demo Link', 3, false);

  m = key.match(/^\[Project (\d+) Description:/);
  if (m) return proj(+m[1], 'Description', 4, true);

  m = key.match(/^\[Project (\d+) Bullet (\d+):/);
  if (m) return proj(+m[1], `Bullet ${m[2]}`, 10 + +m[2], true);

  // ── Education ───────────────────────────────────────────────────────────────
  m = key.match(/^\[Degree\/Program (\d+)\]$/);
  if (m) return edu(+m[1], 'Degree / Program', 0, false);

  m = key.match(/^\[Institution (\d+)\]$/);
  if (m) return edu(+m[1], 'Institution', 1, false);

  m = key.match(/^\[Graduation Year (\d+)\]$/);
  if (m) return edu(+m[1], 'Graduation Year', 2, false);

  m = key.match(/^\[Education (\d+) Details:/);
  if (m) return edu(+m[1], 'Details', 3, true);

  // ── Unrecognized — show in "Other" ──────────────────────────────────────────
  return {
    section: 'Other', sectionOrder: 99,
    group: null, groupIndex: 0,
    label: key.slice(1, -1).split(':')[0].trim(),
    fieldOrder: 0, required: false, multiline: false,
  };
}

function exp(n: number, label: string, fieldOrder: number, multiline: boolean): FieldMeta {
  return { section: 'Experience', sectionOrder: 3, group: `Job ${n}`, groupIndex: n, label, fieldOrder, required: false, multiline };
}
function proj(n: number, label: string, fieldOrder: number, multiline: boolean): FieldMeta {
  return { section: 'Projects', sectionOrder: 4, group: `Project ${n}`, groupIndex: n, label, fieldOrder, required: false, multiline };
}
function edu(n: number, label: string, fieldOrder: number, multiline: boolean): FieldMeta {
  return { section: 'Education', sectionOrder: 5, group: `Education ${n}`, groupIndex: n, label, fieldOrder, required: false, multiline };
}

// ─── Section builder ─────────────────────────────────────────────────────────

interface FieldEntry { key: string; label: string; fieldOrder: number; required: boolean; multiline: boolean; }
interface Group      { name: string | null; groupIndex: number; fields: FieldEntry[]; }
interface Section    { title: string; sectionOrder: number; groups: Group[]; }

function buildSections(keys: string[]): Section[] {
  const map = new Map<string, Section>();

  for (const key of keys) {
    const meta = classifyKey(key);
    if (!meta) continue;

    if (!map.has(meta.section)) {
      map.set(meta.section, { title: meta.section, sectionOrder: meta.sectionOrder, groups: [] });
    }
    const section = map.get(meta.section)!;

    let group = section.groups.find(g => g.name === meta.group);
    if (!group) {
      group = { name: meta.group, groupIndex: meta.groupIndex, fields: [] };
      section.groups.push(group);
    }
    group.fields.push({ key, label: meta.label, fieldOrder: meta.fieldOrder, required: meta.required, multiline: meta.multiline });
  }

  return Array.from(map.values())
    .sort((a, b) => a.sectionOrder - b.sectionOrder)
    .map(sec => ({
      ...sec,
      groups: sec.groups
        .sort((a, b) => a.groupIndex - b.groupIndex)
        .map(grp => ({ ...grp, fields: grp.fields.sort((a, b) => a.fieldOrder - b.fieldOrder) })),
    }));
}

// ─── Component ───────────────────────────────────────────────────────────────

interface ResumeEditorProps {
  initialMap: Record<string, string>;
  onClose: () => void;
  onDirtyChange: (dirty: boolean) => void;
}

export default function ResumeEditor({ initialMap, onClose, onDirtyChange }: ResumeEditorProps) {
  // Keys that Claude set to "REMOVE" — blank in the form, but restored on download
  const removedKeys = useMemo(() => {
    const s = new Set<string>();
    for (const [k, v] of Object.entries(initialMap)) {
      if (v === 'REMOVE') s.add(k);
    }
    return s;
  }, [initialMap]);

  // Display values: "REMOVE" → "" so the user sees a blank field
  const initialDisplay = useMemo(() => {
    const d: Record<string, string> = {};
    for (const [k, v] of Object.entries(initialMap)) d[k] = v === 'REMOVE' ? '' : v;
    return d;
  }, [initialMap]);

  const [values, setValues] = useState<Record<string, string>>(initialDisplay);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [downloading, setDownloading] = useState(false);
  const [downloadError, setDownloadError] = useState<string | null>(null);

  const sections = useMemo(() => buildSections(Object.keys(initialMap)), [initialMap]);

  // Reset dirty flag when initialMap changes (new generation)
  useEffect(() => {
    setValues(initialDisplay);
    setFieldErrors({});
    setDownloadError(null);
    onDirtyChange(false);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [initialMap]);

  function handleChange(key: string, val: string) {
    const next = { ...values, [key]: val };
    setValues(next);
    if (fieldErrors[key]) setFieldErrors(prev => { const e = { ...prev }; delete e[key]; return e; });
    const isDirty = Object.keys(next).some(k => next[k] !== (initialDisplay[k] ?? ''));
    onDirtyChange(isDirty);
  }

  function validate(): Record<string, string> {
    const errs: Record<string, string> = {};
    for (const key of ['[FIRST AND LAST NAME]', '[Phone Number]', '[Professional Email]']) {
      if (key in values && !values[key].trim()) errs[key] = 'This field is required.';
    }
    return errs;
  }

  async function handleDownload() {
    const errs = validate();
    if (Object.keys(errs).length > 0) {
      setFieldErrors(errs);
      // Scroll to first error
      const firstKey = Object.keys(errs)[0];
      document.querySelector(`[data-field-key="${CSS.escape(firstKey)}"]`)?.scrollIntoView({ behavior: 'smooth', block: 'center' });
      return;
    }

    // Restore "REMOVE" for contact fields the user left blank
    const editedMap: Record<string, string> = {};
    for (const [k, v] of Object.entries(values)) {
      editedMap[k] = v.trim() === '' && removedKeys.has(k) ? 'REMOVE' : v;
    }

    setDownloading(true);
    setDownloadError(null);
    try {
      const blob = await apiBlob('/api/export/word', {
        method: 'POST',
        body: JSON.stringify({ content: JSON.stringify(editedMap), format: 'DOCX', documentType: 'RESUME' }),
      });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'resume_edited.docx';
      a.click();
      URL.revokeObjectURL(url);
    } catch (e) {
      setDownloadError(e instanceof ApiError ? e.message : 'Download failed. Please try again.');
    } finally {
      setDownloading(false);
    }
  }

  return (
    <div className="resume-editor">
      <div className="resume-editor__header">
        <div className="resume-editor__header-left">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
            <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
          </svg>
          <h3 className="resume-editor__title">Edit Resume</h3>
        </div>
        <button className="resume-editor__close" onClick={onClose} aria-label="Close editor">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
            <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
          </svg>
        </button>
      </div>

      <p className="resume-editor__hint">
        Fields left blank were intentionally omitted by the AI. Required fields are marked <span className="resume-editor__req-star">*</span>.
      </p>

      {sections.map(section => (
        <div key={section.title} className="editor-section">
          <h4 className="editor-section__title">{section.title}</h4>

          {section.groups.map(group => (
            <div key={group.name ?? '__root__'} className={`editor-group${group.name ? ' editor-group--titled' : ''}`}>
              {group.name && <h5 className="editor-group__label">{group.name}</h5>}

              <div className="editor-group__fields">
                {group.fields.map(field => {
                  const val = values[field.key] ?? '';
                  const isEmpty = val.trim() === '';
                  const hasError = !!fieldErrors[field.key];
                  return (
                    <div
                      key={field.key}
                      className={`editor-field${isEmpty ? ' editor-field--empty' : ''}${hasError ? ' editor-field--error' : ''}`}
                      data-field-key={field.key}
                    >
                      <label className="editor-field__label">
                        {field.label}
                        {field.required && <span className="resume-editor__req-star"> *</span>}
                      </label>
                      {field.multiline ? (
                        <textarea
                          className={`textarea editor-field__textarea${hasError ? ' textarea--error' : ''}`}
                          value={val}
                          onChange={e => handleChange(field.key, e.target.value)}
                          placeholder={isEmpty ? 'Left blank by AI' : undefined}
                          rows={field.label.startsWith('Bullet') ? 2 : 3}
                        />
                      ) : (
                        <input
                          type="text"
                          className={`input editor-field__input${hasError ? ' input--error' : ''}`}
                          value={val}
                          onChange={e => handleChange(field.key, e.target.value)}
                          placeholder={isEmpty ? 'Left blank by AI' : undefined}
                        />
                      )}
                      {hasError && <p className="field-error">{fieldErrors[field.key]}</p>}
                    </div>
                  );
                })}
              </div>
            </div>
          ))}
        </div>
      ))}

      {downloadError && <p className="field-error resume-editor__dl-error">{downloadError}</p>}

      <div className="resume-editor__footer">
        <button
          className="btn btn--primary btn--full"
          onClick={handleDownload}
          disabled={downloading}
        >
          {downloading ? (
            <><span className="spinner-sm" aria-hidden="true" /> Downloading...</>
          ) : (
            <>
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" style={{ marginRight: '0.4em' }}>
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
                <polyline points="7 10 12 15 17 10" />
                <line x1="12" y1="15" x2="12" y2="3" />
              </svg>
              Download Edited DOCX
            </>
          )}
        </button>
      </div>
    </div>
  );
}
