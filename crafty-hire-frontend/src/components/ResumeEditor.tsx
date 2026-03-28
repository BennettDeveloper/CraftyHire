import { useState, useMemo, useEffect, useRef } from 'react';
import {
  DndContext,
  closestCenter,
  PointerSensor,
  useSensor,
  useSensors,
  type DragEndEvent,
} from '@dnd-kit/core';
import {
  SortableContext,
  verticalListSortingStrategy,
  useSortable,
  arrayMove,
} from '@dnd-kit/sortable';
import { CSS as DndCSS } from '@dnd-kit/utilities';
import { apiBlob, ApiError } from '../api/client';

// ─── Types ────────────────────────────────────────────────────────────────────

interface ResumeEditorProps {
  initialMap: Record<string, string>;
  onLiveUpdate: (exportMap: Record<string, string>) => void;
  onSave: (exportMap: Record<string, string>) => void;
  onClose: () => void;
  onDirtyChange: (dirty: boolean) => void;
}

interface Baseline {
  values: Record<string, string>;
  jobOrder: number[];
  projOrder: number[];
  eduOrder: number[];
}

// ─── Pure helpers (module-level, no hooks) ───────────────────────────────────

function getIndices(map: Record<string, string>, pattern: RegExp): number[] {
  const set = new Set<number>();
  for (const key of Object.keys(map)) {
    const m = key.match(pattern);
    if (m) set.add(+m[1]);
  }
  return Array.from(set).sort((a, b) => a - b);
}

function computeInitialDisplay(map: Record<string, string>): Record<string, string> {
  const d: Record<string, string> = {};
  for (const [k, v] of Object.entries(map)) d[k] = v === 'REMOVE' ? '' : v;
  // Ensure website / label fields always exist in state
  const extras = [
    '[Website URL]', '[Other URL]',
    '[LinkedIn URL Label]', '[GitHub URL Label]',
    '[Website URL Label]', '[Other URL Label]',
  ];
  for (const k of extras) if (!(k in d)) d[k] = '';
  return d;
}

function computeActiveOrder(
  map: Record<string, string>,
  pattern: RegExp,
  titleKey: (n: number) => string,
): number[] {
  return getIndices(map, pattern).filter(n => {
    const v = map[titleKey(n)] ?? '';
    return v !== 'REMOVE' && v.trim() !== '';
  });
}

function isLabelKey(key: string): boolean {
  return key.endsWith(' Label]');
}

function isNumberedSectionKey(key: string): boolean {
  return (
    /^\[Job Title \d+\]$/.test(key) ||
    /^\[Company Name \d+\]$/.test(key) ||
    /^\[City, State \d+\]$/.test(key) ||
    /^\[Month Year \d+ (Start|End)\]$/.test(key) ||
    /^\[Job \d+ Bullet \d+:/.test(key) ||
    /^\[Project Name \d+\]$/.test(key) ||
    /^\[Tech Stack \d+\]$/.test(key) ||
    /^\[GitHub Link \d+\]$/.test(key) ||
    /^\[Live Demo Link \d+\]$/.test(key) ||
    /^\[Project \d+ (Description|Bullet)/.test(key) ||
    /^\[Degree\/Program \d+\]$/.test(key) ||
    /^\[Institution \d+\]$/.test(key) ||
    /^\[Graduation Year \d+\]$/.test(key) ||
    /^\[Education \d+ Details:/.test(key)
  );
}

// ── Slot remapping helpers ────────────────────────────────────────────────────

function addJobSlot(
  values: Record<string, string>,
  srcN: number,
  dstN: number,
  out: Record<string, string>,
): void {
  const exact: [string, string][] = [
    [`[Job Title ${srcN}]`,           `[Job Title ${dstN}]`],
    [`[Company Name ${srcN}]`,        `[Company Name ${dstN}]`],
    [`[City, State ${srcN}]`,         `[City, State ${dstN}]`],
    [`[Month Year ${srcN} Start]`,    `[Month Year ${dstN} Start]`],
    [`[Month Year ${srcN} End]`,      `[Month Year ${dstN} End]`],
  ];
  for (const [src, dst] of exact) {
    if (src in values) out[dst] = values[src];
  }
  const bp = `[Job ${srcN} Bullet `;
  const dp = `[Job ${dstN} Bullet `;
  for (const [key, val] of Object.entries(values)) {
    if (key.startsWith(bp)) out[dp + key.slice(bp.length)] = val;
  }
}

function addProjSlot(
  values: Record<string, string>,
  srcN: number,
  dstN: number,
  out: Record<string, string>,
): void {
  const exact: [string, string][] = [
    [`[Project Name ${srcN}]`,    `[Project Name ${dstN}]`],
    [`[Tech Stack ${srcN}]`,      `[Tech Stack ${dstN}]`],
    [`[GitHub Link ${srcN}]`,     `[GitHub Link ${dstN}]`],
    [`[Live Demo Link ${srcN}]`,  `[Live Demo Link ${dstN}]`],
  ];
  for (const [src, dst] of exact) {
    if (src in values) out[dst] = values[src];
  }
  const descPfx = `[Project ${srcN} Description:`;
  const dstDescPfx = `[Project ${dstN} Description:`;
  const bp = `[Project ${srcN} Bullet `;
  const dp = `[Project ${dstN} Bullet `;
  for (const [key, val] of Object.entries(values)) {
    if (key.startsWith(descPfx)) out[dstDescPfx + key.slice(descPfx.length)] = val;
    else if (key.startsWith(bp)) out[dp + key.slice(bp.length)] = val;
  }
}

function addEduSlot(
  values: Record<string, string>,
  srcN: number,
  dstN: number,
  out: Record<string, string>,
): void {
  const exact: [string, string][] = [
    [`[Degree/Program ${srcN}]`,   `[Degree/Program ${dstN}]`],
    [`[Institution ${srcN}]`,      `[Institution ${dstN}]`],
    [`[Graduation Year ${srcN}]`,  `[Graduation Year ${dstN}]`],
  ];
  for (const [src, dst] of exact) {
    if (src in values) out[dst] = values[src];
  }
  const dp = `[Education ${srcN} Details:`;
  const ddp = `[Education ${dstN} Details:`;
  for (const [key, val] of Object.entries(values)) {
    if (key.startsWith(dp)) out[ddp + key.slice(dp.length)] = val;
  }
}

function buildExportMap(
  values: Record<string, string>,
  initialMap: Record<string, string>,
  jobOrder: number[],
  projOrder: number[],
  eduOrder: number[],
): Record<string, string> {
  const out: Record<string, string> = {};

  // Scalar keys: header, summary, skills, new website fields
  for (const [k, v] of Object.entries(values)) {
    if (isLabelKey(k)) continue;
    if (isNumberedSectionKey(k)) continue;
    const wasRemove = (initialMap[k] ?? '') === 'REMOVE';
    out[k] = v.trim() === '' && wasRemove ? 'REMOVE' : v;
  }

  // Numbered sections — remapped to sequential slots
  jobOrder.forEach((srcN, slot) => addJobSlot(values, srcN, slot + 1, out));
  projOrder.forEach((srcN, slot) => addProjSlot(values, srcN, slot + 1, out));
  eduOrder.forEach((srcN, slot) => addEduSlot(values, srcN, slot + 1, out));

  return out;
}

function computeIsDirty(
  values: Record<string, string>,
  jobOrder: number[],
  projOrder: number[],
  eduOrder: number[],
  baseline: Baseline,
): boolean {
  if (jobOrder.join(',')  !== baseline.jobOrder.join(','))  return true;
  if (projOrder.join(',') !== baseline.projOrder.join(',')) return true;
  if (eduOrder.join(',')  !== baseline.eduOrder.join(','))  return true;
  const allKeys = new Set([...Object.keys(values), ...Object.keys(baseline.values)]);
  for (const k of allKeys) {
    if ((values[k] ?? '') !== (baseline.values[k] ?? '')) return true;
  }
  return false;
}

// ─── Drag handle icon ────────────────────────────────────────────────────────

function DragHandleIcon() {
  return (
    <svg width="11" height="14" viewBox="0 0 11 14" fill="currentColor" aria-hidden>
      <circle cx="2.5" cy="2.5"  r="1.3"/>
      <circle cx="8.5" cy="2.5"  r="1.3"/>
      <circle cx="2.5" cy="7"    r="1.3"/>
      <circle cx="8.5" cy="7"    r="1.3"/>
      <circle cx="2.5" cy="11.5" r="1.3"/>
      <circle cx="8.5" cy="11.5" r="1.3"/>
    </svg>
  );
}

// ─── EditorField ─────────────────────────────────────────────────────────────

interface FieldProps {
  label: string;
  fieldKey: string;
  values: Record<string, string>;
  onChange: (key: string, val: string) => void;
  multiline?: boolean;
  required?: boolean;
  rows?: number;
  error?: string;
  placeholder?: string;
}

function EditorField({ label, fieldKey, values, onChange, multiline = false, required = false, rows = 3, error, placeholder }: FieldProps) {
  const val = values[fieldKey] ?? '';
  const isEmpty = val.trim() === '';
  const ph = placeholder ?? (isEmpty ? 'Left blank by AI' : undefined);
  return (
    <div
      className={`editor-field${isEmpty ? ' editor-field--empty' : ''}${error ? ' editor-field--error' : ''}`}
      data-field-key={fieldKey}
    >
      <label className="editor-field__label">
        {label}{required && <span className="resume-editor__req-star"> *</span>}
      </label>
      {multiline ? (
        <textarea
          className={`textarea editor-field__textarea${error ? ' textarea--error' : ''}`}
          value={val}
          onChange={e => onChange(fieldKey, e.target.value)}
          placeholder={ph}
          rows={rows}
        />
      ) : (
        <input
          type="text"
          className={`input editor-field__input${error ? ' input--error' : ''}`}
          value={val}
          onChange={e => onChange(fieldKey, e.target.value)}
          placeholder={ph}
        />
      )}
      {error && <p className="field-error">{error}</p>}
    </div>
  );
}

// ─── Sortable job entry ───────────────────────────────────────────────────────

interface SortableJobProps {
  id: number;
  displayIndex: number;
  values: Record<string, string>;
  bulletKeys: string[];
  onChange: (key: string, val: string) => void;
  onDelete: () => void;
  canDelete: boolean;
  fieldErrors: Record<string, string>;
}

function SortableJobEntry({ id, displayIndex, values, bulletKeys, onChange, onDelete, canDelete, fieldErrors }: SortableJobProps) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({ id });
  const style: React.CSSProperties = { transform: DndCSS.Transform.toString(transform), transition, opacity: isDragging ? 0.4 : 1 };
  const n = id;

  return (
    <div ref={setNodeRef} style={style} className="editor-group editor-group--titled editor-group--sortable">
      <div className="editor-group__drag-row">
        <button className="drag-handle" {...attributes} {...listeners} aria-label="Drag to reorder" type="button">
          <DragHandleIcon />
        </button>
        <h5 className="editor-group__label">Job {displayIndex}</h5>
        {canDelete && (
          <button className="btn-icon btn-icon--delete" onClick={onDelete} aria-label="Delete experience entry" type="button">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <polyline points="3 6 5 6 21 6"/>
              <path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/>
              <path d="M10 11v6M14 11v6"/>
              <path d="M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/>
            </svg>
          </button>
        )}
      </div>

      <div className="editor-group__fields">
        <EditorField label="Job Title"   fieldKey={`[Job Title ${n}]`}        values={values} onChange={onChange} error={fieldErrors[`[Job Title ${n}]`]} />
        <EditorField label="Company"     fieldKey={`[Company Name ${n}]`}     values={values} onChange={onChange} />
        <EditorField label="City, State" fieldKey={`[City, State ${n}]`}      values={values} onChange={onChange} />
        <EditorField label="Start Date"  fieldKey={`[Month Year ${n} Start]`} values={values} onChange={onChange} placeholder="e.g. Jan 2022" />
        <EditorField label="End Date"    fieldKey={`[Month Year ${n} End]`}   values={values} onChange={onChange} placeholder="e.g. Present" />
        {bulletKeys.map((bk, i) => (
          <EditorField key={bk} label={`Bullet ${i + 1}`} fieldKey={bk} values={values} onChange={onChange} multiline rows={2} />
        ))}
      </div>
    </div>
  );
}

// ─── Sortable project entry ───────────────────────────────────────────────────

interface SortableProjProps {
  id: number;
  displayIndex: number;
  values: Record<string, string>;
  descKey: string;
  bulletKeys: string[];
  onChange: (key: string, val: string) => void;
  onDelete: () => void;
  canDelete: boolean;
}

function SortableProjEntry({ id, displayIndex, values, descKey, bulletKeys, onChange, onDelete, canDelete }: SortableProjProps) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({ id });
  const style: React.CSSProperties = { transform: DndCSS.Transform.toString(transform), transition, opacity: isDragging ? 0.4 : 1 };
  const n = id;

  return (
    <div ref={setNodeRef} style={style} className="editor-group editor-group--titled editor-group--sortable">
      <div className="editor-group__drag-row">
        <button className="drag-handle" {...attributes} {...listeners} aria-label="Drag to reorder" type="button">
          <DragHandleIcon />
        </button>
        <h5 className="editor-group__label">Project {displayIndex}</h5>
        {canDelete && (
          <button className="btn-icon btn-icon--delete" onClick={onDelete} aria-label="Delete project entry" type="button">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <polyline points="3 6 5 6 21 6"/>
              <path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/>
              <path d="M10 11v6M14 11v6"/>
              <path d="M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/>
            </svg>
          </button>
        )}
      </div>

      <div className="editor-group__fields">
        <EditorField label="Project Name"    fieldKey={`[Project Name ${n}]`}   values={values} onChange={onChange} />
        <EditorField label="Tech Stack"      fieldKey={`[Tech Stack ${n}]`}     values={values} onChange={onChange} />
        <EditorField label="GitHub Link"     fieldKey={`[GitHub Link ${n}]`}    values={values} onChange={onChange} placeholder="https://github.com/..." />
        <EditorField label="Live Demo Link"  fieldKey={`[Live Demo Link ${n}]`} values={values} onChange={onChange} placeholder="https://..." />
        {descKey && <EditorField label="Description" fieldKey={descKey} values={values} onChange={onChange} multiline rows={3} />}
        {bulletKeys.map((bk, i) => (
          <EditorField key={bk} label={`Bullet ${i + 1}`} fieldKey={bk} values={values} onChange={onChange} multiline rows={2} />
        ))}
      </div>
    </div>
  );
}

// ─── Sortable education entry ─────────────────────────────────────────────────

interface SortableEduProps {
  id: number;
  displayIndex: number;
  values: Record<string, string>;
  detailsKey: string;
  onChange: (key: string, val: string) => void;
  onDelete: () => void;
  canDelete: boolean;
}

function SortableEduEntry({ id, displayIndex, values, detailsKey, onChange, onDelete, canDelete }: SortableEduProps) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({ id });
  const style: React.CSSProperties = { transform: DndCSS.Transform.toString(transform), transition, opacity: isDragging ? 0.4 : 1 };
  const n = id;

  return (
    <div ref={setNodeRef} style={style} className="editor-group editor-group--titled editor-group--sortable">
      <div className="editor-group__drag-row">
        <button className="drag-handle" {...attributes} {...listeners} aria-label="Drag to reorder" type="button">
          <DragHandleIcon />
        </button>
        <h5 className="editor-group__label">Education {displayIndex}</h5>
        {canDelete && (
          <button className="btn-icon btn-icon--delete" onClick={onDelete} aria-label="Delete education entry" type="button">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <polyline points="3 6 5 6 21 6"/>
              <path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/>
              <path d="M10 11v6M14 11v6"/>
              <path d="M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/>
            </svg>
          </button>
        )}
      </div>

      <div className="editor-group__fields">
        <EditorField label="Degree / Program"  fieldKey={`[Degree/Program ${n}]`}  values={values} onChange={onChange} />
        <EditorField label="Institution"        fieldKey={`[Institution ${n}]`}     values={values} onChange={onChange} />
        <EditorField label="Graduation Year"    fieldKey={`[Graduation Year ${n}]`} values={values} onChange={onChange} placeholder="e.g. 2024" />
        {detailsKey && <EditorField label="Details" fieldKey={detailsKey} values={values} onChange={onChange} multiline rows={2} />}
      </div>
    </div>
  );
}

// ─── Skill prefixes ───────────────────────────────────────────────────────────

const SKILLS_PREFIXES = [
  { prefix: '[Languages:',           label: 'Languages' },
  { prefix: '[Frameworks:',          label: 'Frameworks' },
  { prefix: '[Databases:',           label: 'Databases' },
  { prefix: '[Tools:',               label: 'Tools & Platforms' },
  { prefix: '[Methodologies:',       label: 'Methodologies' },
  { prefix: '[Professional Skills:', label: 'Professional Skills' },
  { prefix: '[Certifications:',      label: 'Certifications' },
];

// ─── Main component ───────────────────────────────────────────────────────────

export default function ResumeEditor({ initialMap, onLiveUpdate, onSave, onClose, onDirtyChange }: ResumeEditorProps) {

  // ── Stable derived values ──────────────────────────────────────────────────
  const allJobIndices  = useMemo(() => getIndices(initialMap, /^\[Job Title (\d+)\]$/), [initialMap]);
  const allProjIndices = useMemo(() => getIndices(initialMap, /^\[Project Name (\d+)\]$/), [initialMap]);
  const allEduIndices  = useMemo(() => getIndices(initialMap, /^\[Degree\/Program (\d+)\]$/), [initialMap]);

  // ── State ──────────────────────────────────────────────────────────────────
  const [values,    setValues]    = useState(() => computeInitialDisplay(initialMap));
  const [jobOrder,  setJobOrder]  = useState(() => computeActiveOrder(initialMap, /^\[Job Title (\d+)\]$/,      n => `[Job Title ${n}]`));
  const [projOrder, setProjOrder] = useState(() => computeActiveOrder(initialMap, /^\[Project Name (\d+)\]$/,  n => `[Project Name ${n}]`));
  const [eduOrder,  setEduOrder]  = useState(() => computeActiveOrder(initialMap, /^\[Degree\/Program (\d+)\]$/, n => `[Degree/Program ${n}]`));

  const [fieldErrors,    setFieldErrors]    = useState<Record<string, string>>({});
  const [downloading,    setDownloading]    = useState(false);
  const [downloadError,  setDownloadError]  = useState<string | null>(null);
  const [showCloseDialog, setShowCloseDialog] = useState(false);

  // Baseline for dirty tracking (last saved state)
  const baselineRef = useRef<Baseline | null>(null);
  if (baselineRef.current === null) {
    baselineRef.current = {
      values:    computeInitialDisplay(initialMap),
      jobOrder:  computeActiveOrder(initialMap, /^\[Job Title (\d+)\]$/,      n => `[Job Title ${n}]`),
      projOrder: computeActiveOrder(initialMap, /^\[Project Name (\d+)\]$/,  n => `[Project Name ${n}]`),
      eduOrder:  computeActiveOrder(initialMap, /^\[Degree\/Program (\d+)\]$/, n => `[Degree/Program ${n}]`),
    };
  }

  // DnD sensors
  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 5 } }));

  // Emit initial live map on mount so DocumentResult is in sync immediately
  useEffect(() => {
    onLiveUpdate(buildExportMap(values, initialMap, jobOrder, projOrder, eduOrder));
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // ── Helpers ────────────────────────────────────────────────────────────────

  function emitLive(v = values, j = jobOrder, p = projOrder, e = eduOrder) {
    onLiveUpdate(buildExportMap(v, initialMap, j, p, e));
  }

  function emitDirty(v = values, j = jobOrder, p = projOrder, e = eduOrder) {
    onDirtyChange(computeIsDirty(v, j, p, e, baselineRef.current!));
  }

  // ── Field change ───────────────────────────────────────────────────────────

  function handleChange(key: string, val: string) {
    const next = { ...values, [key]: val };
    setValues(next);
    if (fieldErrors[key]) setFieldErrors(prev => { const e = { ...prev }; delete e[key]; return e; });
    emitLive(next);
    emitDirty(next);
  }

  // ── DnD drag end handlers ──────────────────────────────────────────────────

  function handleJobDragEnd(event: DragEndEvent) {
    const { active, over } = event;
    if (!over || active.id === over.id) return;
    const next = arrayMove(jobOrder, jobOrder.indexOf(+active.id), jobOrder.indexOf(+over.id));
    setJobOrder(next);
    emitLive(values, next);
    emitDirty(values, next);
  }

  function handleProjDragEnd(event: DragEndEvent) {
    const { active, over } = event;
    if (!over || active.id === over.id) return;
    const next = arrayMove(projOrder, projOrder.indexOf(+active.id), projOrder.indexOf(+over.id));
    setProjOrder(next);
    emitLive(values, jobOrder, next);
    emitDirty(values, jobOrder, next);
  }

  function handleEduDragEnd(event: DragEndEvent) {
    const { active, over } = event;
    if (!over || active.id === over.id) return;
    const next = arrayMove(eduOrder, eduOrder.indexOf(+active.id), eduOrder.indexOf(+over.id));
    setEduOrder(next);
    emitLive(values, jobOrder, projOrder, next);
    emitDirty(values, jobOrder, projOrder, next);
  }

  // ── Add / delete ───────────────────────────────────────────────────────────

  function handleAddJob() {
    const available = allJobIndices.filter(n => !jobOrder.includes(n));
    if (!available.length) return;
    const next = [...jobOrder, available[0]];
    setJobOrder(next);
    emitLive(values, next);
    emitDirty(values, next);
  }

  function handleDeleteJob(idx: number) {
    if (jobOrder.length <= 1) return;
    const next = jobOrder.filter(n => n !== idx);
    setJobOrder(next);
    emitLive(values, next);
    emitDirty(values, next);
  }

  function handleAddProj() {
    const available = allProjIndices.filter(n => !projOrder.includes(n));
    if (!available.length) return;
    const next = [...projOrder, available[0]];
    setProjOrder(next);
    emitLive(values, jobOrder, next);
    emitDirty(values, jobOrder, next);
  }

  function handleDeleteProj(idx: number) {
    const next = projOrder.filter(n => n !== idx);
    setProjOrder(next);
    emitLive(values, jobOrder, next);
    emitDirty(values, jobOrder, next);
  }

  function handleAddEdu() {
    const available = allEduIndices.filter(n => !eduOrder.includes(n));
    if (!available.length) return;
    const next = [...eduOrder, available[0]];
    setEduOrder(next);
    emitLive(values, jobOrder, projOrder, next);
    emitDirty(values, jobOrder, projOrder, next);
  }

  function handleDeleteEdu(idx: number) {
    if (eduOrder.length <= 1) return;
    const next = eduOrder.filter(n => n !== idx);
    setEduOrder(next);
    emitLive(values, jobOrder, projOrder, next);
    emitDirty(values, jobOrder, projOrder, next);
  }

  // ── Save ───────────────────────────────────────────────────────────────────

  function handleSave() {
    const map = buildExportMap(values, initialMap, jobOrder, projOrder, eduOrder);
    baselineRef.current = {
      values: { ...values },
      jobOrder: [...jobOrder],
      projOrder: [...projOrder],
      eduOrder: [...eduOrder],
    };
    onSave(map);
    onDirtyChange(false);
  }

  // ── Close ──────────────────────────────────────────────────────────────────

  function handleCloseClick() {
    const dirty = computeIsDirty(values, jobOrder, projOrder, eduOrder, baselineRef.current!);
    if (dirty) { setShowCloseDialog(true); return; }
    onClose();
  }

  function handleSaveAndClose() {
    handleSave();
    setShowCloseDialog(false);
    onClose();
  }

  function handleDiscardAndClose() {
    setShowCloseDialog(false);
    onDirtyChange(false);
    onClose();
  }

  // ── Download ───────────────────────────────────────────────────────────────

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
      const firstKey = Object.keys(errs)[0];
      document.querySelector(`[data-field-key="${CSS.escape(firstKey)}"]`)?.scrollIntoView({ behavior: 'smooth', block: 'center' });
      return;
    }
    const editedMap = buildExportMap(values, initialMap, jobOrder, projOrder, eduOrder);
    setDownloading(true);
    setDownloadError(null);
    try {
      const blob = await apiBlob('/api/export/word', {
        method: 'POST',
        body: JSON.stringify({ content: JSON.stringify(editedMap), format: 'DOCX', documentType: 'RESUME' }),
      });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url; a.download = 'resume_edited.docx'; a.click();
      URL.revokeObjectURL(url);
    } catch (e) {
      setDownloadError(e instanceof ApiError ? e.message : 'Download failed. Please try again.');
    } finally {
      setDownloading(false);
    }
  }

  // ── Derived: key lookups for sections ─────────────────────────────────────

  function jobBulletKeys(n: number): string[] {
    const bp = `[Job ${n} Bullet `;
    const bullets: { idx: number; key: string }[] = [];
    for (const key of Object.keys(values)) {
      if (key.startsWith(bp)) {
        const m = key.match(/Bullet (\d+):/);
        if (m) bullets.push({ idx: +m[1], key });
      }
    }
    return bullets.sort((a, b) => a.idx - b.idx).map(b => b.key);
  }

  function projDescKey(n: number): string {
    return Object.keys(values).find(k => k.startsWith(`[Project ${n} Description:`)) ?? '';
  }

  function projBulletKeys(n: number): string[] {
    const bp = `[Project ${n} Bullet `;
    const bullets: { idx: number; key: string }[] = [];
    for (const key of Object.keys(values)) {
      if (key.startsWith(bp)) {
        const m = key.match(/Bullet (\d+):/);
        if (m) bullets.push({ idx: +m[1], key });
      }
    }
    return bullets.sort((a, b) => a.idx - b.idx).map(b => b.key);
  }

  function eduDetailsKey(n: number): string {
    return Object.keys(values).find(k => k.startsWith(`[Education ${n} Details:`)) ?? '';
  }

  function skillKey(prefix: string): string {
    return Object.keys(values).find(k => k.startsWith(prefix)) ?? '';
  }

  function summaryKey(): string {
    return Object.keys(values).find(k => k.startsWith('[Professional Summary:')) ?? '';
  }

  // ── Render ─────────────────────────────────────────────────────────────────

  return (
    <div className="resume-editor">

      {/* ── Header bar ──────────────────────────────────────────────────────── */}
      <div className="resume-editor__header">
        <div className="resume-editor__header-left">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
            <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
          </svg>
          <h3 className="resume-editor__title">Edit Resume</h3>
        </div>
        <button className="resume-editor__close" onClick={handleCloseClick} aria-label="Close editor">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
            <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
          </svg>
        </button>
      </div>

      <p className="resume-editor__hint">
        Fields left blank were intentionally omitted by the AI. Required fields are marked <span className="resume-editor__req-star">*</span>.
        Changes sync to the preview instantly.
      </p>

      {/* ── HEADER ─────────────────────────────────────────────────────────── */}
      <div className="editor-section">
        <h4 className="editor-section__title">Header</h4>

        <div className="editor-group editor-group--titled">
          <h5 className="editor-group__label">Personal Information</h5>
          <div className="editor-group__fields">
            <EditorField label="Full Name"   fieldKey="[FIRST AND LAST NAME]" values={values} onChange={handleChange} required error={fieldErrors['[FIRST AND LAST NAME]']} />
            <EditorField label="Phone"       fieldKey="[Phone Number]"        values={values} onChange={handleChange} required error={fieldErrors['[Phone Number]']} />
            <EditorField label="Email"       fieldKey="[Professional Email]"  values={values} onChange={handleChange} required error={fieldErrors['[Professional Email]']} />
            <EditorField label="City, State" fieldKey="[City, State]"         values={values} onChange={handleChange} />
          </div>
        </div>

        <div className="editor-group editor-group--titled">
          <h5 className="editor-group__label">Websites</h5>
          <div className="editor-group__fields editor-group__fields--websites">
            {/* LinkedIn */}
            <div className="website-field-pair">
              <EditorField label="LinkedIn URL"   fieldKey="[LinkedIn URL]"       values={values} onChange={handleChange} placeholder="https://linkedin.com/in/..." />
              <EditorField label="LinkedIn Label" fieldKey="[LinkedIn URL Label]" values={values} onChange={handleChange} placeholder="Display name (optional)" />
            </div>
            {/* GitHub */}
            <div className="website-field-pair">
              <EditorField label="GitHub URL"   fieldKey="[GitHub URL]"       values={values} onChange={handleChange} placeholder="https://github.com/..." />
              <EditorField label="GitHub Label" fieldKey="[GitHub URL Label]" values={values} onChange={handleChange} placeholder="Display name (optional)" />
            </div>
            {/* Personal website */}
            <div className="website-field-pair">
              <EditorField label="Website URL"   fieldKey="[Website URL]"       values={values} onChange={handleChange} placeholder="https://yoursite.com" />
              <EditorField label="Website Label" fieldKey="[Website URL Label]" values={values} onChange={handleChange} placeholder="Display name (optional)" />
            </div>
            {/* Other */}
            <div className="website-field-pair">
              <EditorField label="Other URL"   fieldKey="[Other URL]"       values={values} onChange={handleChange} placeholder="Any other link" />
              <EditorField label="Other Label" fieldKey="[Other URL Label]" values={values} onChange={handleChange} placeholder="Display name (optional)" />
            </div>
          </div>
        </div>
      </div>

      {/* ── PROFESSIONAL SUMMARY ────────────────────────────────────────────── */}
      {summaryKey() && (
        <div className="editor-section">
          <h4 className="editor-section__title">Professional Summary</h4>
          <div className="editor-group">
            <div className="editor-group__fields">
              <EditorField label="Summary" fieldKey={summaryKey()} values={values} onChange={handleChange} multiline rows={4} />
            </div>
          </div>
        </div>
      )}

      {/* ── SKILLS ─────────────────────────────────────────────────────────── */}
      <div className="editor-section">
        <h4 className="editor-section__title">Skills &amp; Certifications</h4>
        <div className="editor-group">
          <div className="editor-group__fields">
            {SKILLS_PREFIXES.map(s => {
              const key = skillKey(s.prefix);
              return key ? (
                <EditorField key={key} label={s.label} fieldKey={key} values={values} onChange={handleChange} multiline rows={2} />
              ) : null;
            })}
          </div>
        </div>
      </div>

      {/* ── EXPERIENCE ─────────────────────────────────────────────────────── */}
      <div className="editor-section">
        <h4 className="editor-section__title">Experience</h4>
        <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleJobDragEnd}>
          <SortableContext items={jobOrder} strategy={verticalListSortingStrategy}>
            {jobOrder.map((idx, pos) => (
              <SortableJobEntry
                key={idx}
                id={idx}
                displayIndex={pos + 1}
                values={values}
                bulletKeys={jobBulletKeys(idx)}
                onChange={handleChange}
                onDelete={() => handleDeleteJob(idx)}
                canDelete={jobOrder.length > 1}
                fieldErrors={fieldErrors}
              />
            ))}
          </SortableContext>
        </DndContext>
        {jobOrder.length < allJobIndices.length && (
          <button className="btn btn--add-entry" onClick={handleAddJob} type="button">
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" style={{ marginRight: '0.35em' }}>
              <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
            </svg>
            Add Experience
          </button>
        )}
      </div>

      {/* ── PROJECTS ───────────────────────────────────────────────────────── */}
      <div className="editor-section">
        <h4 className="editor-section__title">Projects</h4>
        <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleProjDragEnd}>
          <SortableContext items={projOrder} strategy={verticalListSortingStrategy}>
            {projOrder.map((idx, pos) => (
              <SortableProjEntry
                key={idx}
                id={idx}
                displayIndex={pos + 1}
                values={values}
                descKey={projDescKey(idx)}
                bulletKeys={projBulletKeys(idx)}
                onChange={handleChange}
                onDelete={() => handleDeleteProj(idx)}
                canDelete={true}  // projects min = 0
              />
            ))}
          </SortableContext>
        </DndContext>
        {projOrder.length < allProjIndices.length && (
          <button className="btn btn--add-entry" onClick={handleAddProj} type="button">
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" style={{ marginRight: '0.35em' }}>
              <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
            </svg>
            Add Project
          </button>
        )}
      </div>

      {/* ── EDUCATION ──────────────────────────────────────────────────────── */}
      <div className="editor-section">
        <h4 className="editor-section__title">Education</h4>
        <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleEduDragEnd}>
          <SortableContext items={eduOrder} strategy={verticalListSortingStrategy}>
            {eduOrder.map((idx, pos) => (
              <SortableEduEntry
                key={idx}
                id={idx}
                displayIndex={pos + 1}
                values={values}
                detailsKey={eduDetailsKey(idx)}
                onChange={handleChange}
                onDelete={() => handleDeleteEdu(idx)}
                canDelete={eduOrder.length > 1}
              />
            ))}
          </SortableContext>
        </DndContext>
        {eduOrder.length < allEduIndices.length && (
          <button className="btn btn--add-entry" onClick={handleAddEdu} type="button">
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" style={{ marginRight: '0.35em' }}>
              <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
            </svg>
            Add Education
          </button>
        )}
      </div>

      {/* ── Errors / Footer ────────────────────────────────────────────────── */}
      {downloadError && <p className="field-error resume-editor__dl-error">{downloadError}</p>}

      <div className="resume-editor__footer">
        <button
          className="btn btn--secondary btn--half"
          onClick={handleSave}
          type="button"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" style={{ marginRight: '0.35em' }}>
            <path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"/>
            <polyline points="17 21 17 13 7 13 7 21"/>
            <polyline points="7 3 7 8 15 8"/>
          </svg>
          Save Changes
        </button>
        <button
          className="btn btn--primary btn--half"
          onClick={handleDownload}
          disabled={downloading}
          type="button"
        >
          {downloading ? (
            <><span className="spinner-sm" aria-hidden /> Downloading...</>
          ) : (
            <>
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" style={{ marginRight: '0.35em' }}>
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                <polyline points="7 10 12 15 17 10"/>
                <line x1="12" y1="15" x2="12" y2="3"/>
              </svg>
              Download DOCX
            </>
          )}
        </button>
      </div>

      {/* ── Close confirmation dialog ────────────────────────────────────────── */}
      {showCloseDialog && (
        <div className="editor-close-overlay">
          <div className="editor-close-dialog">
            <p className="editor-close-dialog__msg">You have unsaved changes. Save before closing?</p>
            <div className="editor-close-dialog__actions">
              <button className="btn btn--primary"   onClick={handleSaveAndClose}                    type="button">Save &amp; Close</button>
              <button className="btn btn--secondary" onClick={handleDiscardAndClose}                 type="button">Discard &amp; Close</button>
              <button className="btn btn--ghost"     onClick={() => setShowCloseDialog(false)} type="button">Cancel</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
