import type { SkillScore } from '../types';
import ProgressBar, { STEPS_ANALYZE_GAPS } from './ProgressBar';

interface SkillListProps {
  skills: SkillScore[];
  onAnalyzeGaps: () => void;
  loading: boolean;
}

export default function SkillList({ skills, onAnalyzeGaps, loading }: SkillListProps) {
  // Group skills by category
  const grouped = skills.reduce<Record<string, SkillScore[]>>((acc, skill) => {
    const cat = skill.category || 'Other';
    if (!acc[cat]) acc[cat] = [];
    acc[cat].push(skill);
    return acc;
  }, {});

  // Sort each category by relevance descending
  Object.values(grouped).forEach((group) =>
    group.sort((a, b) => b.relevanceScore - a.relevanceScore),
  );

  return (
    <div className="right-panel">
      <div className="right-panel__header">
        <svg className="right-panel__icon" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <polyline points="22 12 18 12 15 21 9 3 6 12 2 12" />
        </svg>
        <h2 className="right-panel__title">Job Analysis</h2>
      </div>

      <div className="skill-list">
        {Object.entries(grouped).map(([category, categorySkills]) => (
          <div key={category} className="skill-group">
            <h3 className="skill-group__label">{category}</h3>
            {categorySkills.map((skill) => {
              const pct = Math.round(skill.relevanceScore * 100);
              return (
                <div key={skill.skillName} className="skill-row">
                  <div className="skill-row__meta">
                    <span className="skill-row__name">{skill.skillName}</span>
                    <span className="skill-row__pct">{pct}%</span>
                  </div>
                  <div className="skill-bar">
                    <div
                      className="skill-bar__fill"
                      style={{ width: `${pct}%` }}
                    />
                  </div>
                </div>
              );
            })}
          </div>
        ))}
      </div>

      <button
        className="btn btn--primary btn--full"
        onClick={onAnalyzeGaps}
        disabled={loading}
        style={{ marginTop: '1.25rem' }}
      >
        {loading ? 'Analyzing...' : 'Analyze Skill Gaps'}
      </button>
      {loading && (
        <ProgressBar steps={STEPS_ANALYZE_GAPS} active={loading} variant="inline" />
      )}
    </div>
  );
}
