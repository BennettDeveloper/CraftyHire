interface JobPostingCardProps {
  jobDescription: string;
  onJobDescription: (text: string) => void;
  onAnalyze: () => void;
  loading: boolean;
  canAnalyze: boolean;
}

export default function JobPostingCard({
  jobDescription,
  onJobDescription,
  onAnalyze,
  loading,
  canAnalyze,
}: JobPostingCardProps) {
  return (
    <div className="card">
      <div className="card__header">
        {/* Briefcase icon */}
        <svg className="card__icon" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <rect x="2" y="7" width="20" height="14" rx="2" ry="2" />
          <path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16" />
        </svg>
        <h2 className="card__title">Job Posting</h2>
      </div>

      <label className="field-label">Paste the job description text below</label>
      <textarea
        className="textarea"
        placeholder="Paste the job posting here..."
        value={jobDescription}
        onChange={(e) => onJobDescription(e.target.value)}
        rows={8}
      />

      <button
        className="btn btn--primary btn--full"
        onClick={onAnalyze}
        disabled={!canAnalyze || loading}
      >
        {loading ? 'Analyzing...' : 'Analyze Job Posting'}
      </button>
    </div>
  );
}
