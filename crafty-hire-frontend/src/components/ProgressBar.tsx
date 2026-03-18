import { useEffect, useRef, useState } from 'react';

export interface ProgressStep {
  label: string;
  targetPct: number; // 0–100
  durationMs: number; // how long before advancing to next step
}

// Step definitions per operation type — defined here so callers don't need to think about them
export const STEPS_ANALYZE_JOB: ProgressStep[] = [
  { label: 'Reading job description...', targetPct: 18, durationMs: 1400 },
  { label: 'Extracting key requirements...', targetPct: 38, durationMs: 1800 },
  { label: 'Identifying technical skills...', targetPct: 58, durationMs: 2000 },
  { label: 'Ranking skill importance...', targetPct: 78, durationMs: 2000 },
  { label: 'Finalizing analysis...', targetPct: 92, durationMs: 1500 },
];

export const STEPS_ANALYZE_GAPS: ProgressStep[] = [
  { label: 'Comparing resume to job requirements...', targetPct: 25, durationMs: 1600 },
  { label: 'Identifying skill gaps...', targetPct: 55, durationMs: 2000 },
  { label: 'Generating follow-up questions...', targetPct: 80, durationMs: 2000 },
  { label: 'Finalizing gap analysis...', targetPct: 92, durationMs: 1200 },
];

export const STEPS_GENERATE_RESUME: ProgressStep[] = [
  { label: 'Analyzing your experience...', targetPct: 18, durationMs: 1800 },
  { label: 'Tailoring to job requirements...', targetPct: 42, durationMs: 3000 },
  { label: 'Optimizing for ATS systems...', targetPct: 65, durationMs: 3000 },
  { label: 'Formatting document...', targetPct: 85, durationMs: 2500 },
  { label: 'Almost done...', targetPct: 93, durationMs: 1500 },
];

export const STEPS_GENERATE_COVER_LETTER: ProgressStep[] = [
  { label: 'Analyzing your background...', targetPct: 18, durationMs: 1800 },
  { label: 'Crafting your narrative...', targetPct: 45, durationMs: 3000 },
  { label: 'Highlighting relevant skills...', targetPct: 68, durationMs: 2500 },
  { label: 'Polishing tone and style...', targetPct: 88, durationMs: 2500 },
  { label: 'Almost done...', targetPct: 93, durationMs: 1500 },
];

interface ProgressBarProps {
  steps: ProgressStep[];
  active: boolean;
  /** 'panel' = full centered card layout, 'inline' = compact bar + label below button */
  variant?: 'panel' | 'inline';
}

export default function ProgressBar({ steps, active, variant = 'panel' }: ProgressBarProps) {
  const [stepIdx, setStepIdx] = useState(0);
  const [pct, setPct] = useState(0);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (timerRef.current) clearTimeout(timerRef.current);

    if (!active) {
      setStepIdx(0);
      setPct(0);
      return;
    }

    setStepIdx(0);
    setPct(steps[0]?.targetPct ?? 0);

    let i = 0;
    function advance() {
      i++;
      if (i >= steps.length) return;
      setStepIdx(i);
      setPct(steps[i].targetPct);
      timerRef.current = setTimeout(advance, steps[i].durationMs);
    }

    timerRef.current = setTimeout(advance, steps[0]?.durationMs ?? 1200);
    return () => { if (timerRef.current) clearTimeout(timerRef.current); };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [active]);

  const label = steps[stepIdx]?.label ?? '';

  if (variant === 'inline') {
    return (
      <div className="pbar-inline">
        <div className="pbar-track">
          <div className="pbar-fill" style={{ width: `${pct}%` }} />
        </div>
        <p className="pbar-label">{label}</p>
      </div>
    );
  }

  return (
    <div className="pbar-panel">
      <div className="pbar-panel__icon" aria-hidden="true">
        <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
          <path d="M12 2l2.4 7.4H22l-6.2 4.5 2.4 7.4L12 17l-6.2 4.3 2.4-7.4L2 9.4h7.6z" />
        </svg>
      </div>
      <p className="pbar-panel__label">{label}</p>
      <div className="pbar-track pbar-track--wide">
        <div className="pbar-fill" style={{ width: `${pct}%` }} />
      </div>
      <p className="pbar-panel__pct">{pct}%</p>
    </div>
  );
}
