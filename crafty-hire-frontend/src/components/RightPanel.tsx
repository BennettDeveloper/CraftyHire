import type {
  AppState,
  JobAnalysisResponse,
  SkillAnalysisResponse,
  SkillAnswer,
  GenerateDocumentResponse,
  ExportFormat,
  DocumentType,
} from '../types';
import SkillList from './SkillList';
import QuestionFlow from './QuestionFlow';
import DocumentResult from './DocumentResult';

interface RightPanelProps {
  appState: AppState;
  jobAnalysis: JobAnalysisResponse | null;
  skillAnalysis: SkillAnalysisResponse | null;
  currentQuestionIndex: number;
  resume: GenerateDocumentResponse | null;
  coverLetter: GenerateDocumentResponse | null;
  selectedFormat: ExportFormat;
  onFormatChange: (f: ExportFormat) => void;
  onAnalyzeGaps: () => void;
  onAnswer: (answer: SkillAnswer) => void;
  onGenerate: (type: DocumentType) => void;
  onRegenerate: (type: DocumentType) => void;
  loading: boolean;
}

export default function RightPanel({
  appState,
  jobAnalysis,
  skillAnalysis,
  currentQuestionIndex,
  resume,
  coverLetter,
  selectedFormat,
  onFormatChange,
  onAnalyzeGaps,
  onAnswer,
  onGenerate,
  onRegenerate,
  loading,
}: RightPanelProps) {
  if (appState === 'idle') {
    return (
      <div className="panel-placeholder">
        <span className="panel-placeholder__sparkle" aria-hidden="true">
          <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
            <path d="M12 2l2.4 7.4H22l-6.2 4.5 2.4 7.4L12 17l-6.2 4.3 2.4-7.4L2 9.4h7.6z" />
          </svg>
        </span>
        <h2 className="panel-placeholder__title">Get Started</h2>
        <p className="panel-placeholder__body">
          Paste your resume and job posting, then click &quot;Analyze Job Posting&quot; to see skill
          matches and generate optimized documents.
        </p>
      </div>
    );
  }

  if (appState === 'analyzing') {
    return <LoadingCard label="Analyzing job posting..." />;
  }

  if (appState === 'skill_results' && jobAnalysis) {
    return (
      <SkillList
        skills={jobAnalysis.skills}
        analysis={jobAnalysis.analysis}
        onAnalyzeGaps={onAnalyzeGaps}
        loading={loading}
      />
    );
  }

  if (appState === 'questioning' && skillAnalysis) {
    return (
      <QuestionFlow
        questions={skillAnalysis.questions}
        currentIndex={currentQuestionIndex}
        onAnswer={onAnswer}
      />
    );
  }

  if (appState === 'ready') {
    return (
      <div className="right-panel">
        <div className="right-panel__header">
          <svg className="right-panel__icon" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M12 2l2.4 7.4H22l-6.2 4.5 2.4 7.4L12 17l-6.2 4.3 2.4-7.4L2 9.4h7.6z" />
          </svg>
          <h2 className="right-panel__title">Ready to Generate</h2>
        </div>

        <label className="field-label">Output format</label>
        <div className="format-row">
          {(['PDF', 'DOCX', 'LATEX'] as ExportFormat[]).map((f) => (
            <button
              key={f}
              className={`format-btn${selectedFormat === f ? ' format-btn--active' : ''}`}
              onClick={() => onFormatChange(f)}
            >
              {f}
            </button>
          ))}
        </div>

        <button
          className="btn btn--primary btn--full"
          onClick={() => onGenerate('RESUME')}
          disabled={loading}
          style={{ marginTop: '1rem' }}
        >
          {loading ? 'Generating...' : 'Generate Resume'}
        </button>
        <button
          className="btn btn--secondary btn--full"
          onClick={() => onGenerate('COVER_LETTER')}
          disabled={loading}
          style={{ marginTop: '0.75rem' }}
        >
          {loading ? 'Generating...' : 'Generate Cover Letter'}
        </button>
      </div>
    );
  }

  if (appState === 'generating') {
    return <LoadingCard label="Generating document..." />;
  }

  if (appState === 'results') {
    return (
      <DocumentResult
        resume={resume}
        coverLetter={coverLetter}
        onRegenerate={onRegenerate}
        generating={loading}
      />
    );
  }

  return null;
}

function LoadingCard({ label }: { label: string }) {
  return (
    <div className="panel-placeholder">
      <div className="spinner" aria-hidden="true" />
      <p className="panel-placeholder__body">{label}</p>
    </div>
  );
}
