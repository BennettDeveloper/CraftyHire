import { useState, useEffect, useCallback } from 'react';
import type {
  AppState,
  JobAnalysisResponse,
  SkillAnalysisResponse,
  SkillAnswer,
  GenerateDocumentResponse,
  ExportFormat,
  DocumentType,
} from './types';
import { analyzeJob, generateResume, generateCoverLetter } from './api/resume';
import { analyzeSkills } from './api/skills';
import { logout } from './api/auth';
import { hasAccessToken } from './api/client';
import Header from './components/Header';
import ResumeCard from './components/ResumeCard';
import JobPostingCard from './components/JobPostingCard';
import CoverLetterCard from './components/CoverLetterCard';
import RightPanel from './components/RightPanel';
import AuthPage from './components/AuthPage';
import './App.css';

export default function App() {
  // ─── Auth ──────────────────────────────────────────────────────────────────
  const [userEmail, setUserEmail] = useState<string | null>(null);
  const [authChecked, setAuthChecked] = useState(false);

  // On mount, try to restore session via stored refresh token
  useEffect(() => {
    const storedEmail = localStorage.getItem('userEmail');
    if (hasAccessToken() && storedEmail) {
      setUserEmail(storedEmail);
    }
    setAuthChecked(true);

    // Listen for session expiry events from api/client
    const onExpired = () => {
      setUserEmail(null);
      localStorage.removeItem('userEmail');
    };
    window.addEventListener('auth:expired', onExpired);
    return () => window.removeEventListener('auth:expired', onExpired);
  }, []);

  function handleAuth(email: string) {
    localStorage.setItem('userEmail', email);
    setUserEmail(email);
  }

  async function handleLogout() {
    await logout();
    localStorage.removeItem('userEmail');
    setUserEmail(null);
    resetWorkspace();
  }

  // ─── Workspace state ───────────────────────────────────────────────────────
  const [appState, setAppState] = useState<AppState>('idle');
  const [resumeText, setResumeText] = useState('');
  const [jobDescription, setJobDescription] = useState('');
  const [previousCoverLetter, setPreviousCoverLetter] = useState('');
  const [jobAnalysis, setJobAnalysis] = useState<JobAnalysisResponse | null>(null);
  const [skillAnalysis, setSkillAnalysis] = useState<SkillAnalysisResponse | null>(null);
  const [currentQuestionIndex, setCurrentQuestionIndex] = useState(0);
  const [skillAnswers, setSkillAnswers] = useState<SkillAnswer[]>([]);
  const [selectedFormat, setSelectedFormat] = useState<ExportFormat>('DOCX');
  const [generatedResume, setGeneratedResume] = useState<GenerateDocumentResponse | null>(null);
  const [generatedCoverLetter, setGeneratedCoverLetter] = useState<GenerateDocumentResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [generatingType, setGeneratingType] = useState<DocumentType | null>(null);

  function resetWorkspace() {
    setAppState('idle');
    setResumeText('');
    setJobDescription('');
    setPreviousCoverLetter('');
    setJobAnalysis(null);
    setSkillAnalysis(null);
    setCurrentQuestionIndex(0);
    setSkillAnswers([]);
    setGeneratedResume(null);
    setGeneratedCoverLetter(null);
    setError(null);
  }

  // ─── Analyze job posting ───────────────────────────────────────────────────
  async function handleAnalyze() {
    setError(null);
    setAppState('analyzing');
    setLoading(true);
    try {
      const analysis = await analyzeJob(jobDescription);
      setJobAnalysis(analysis);
      setAppState('skill_results');
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Analysis failed.');
      setAppState('idle');
    } finally {
      setLoading(false);
    }
  }

  // ─── Analyze skill gaps ────────────────────────────────────────────────────
  async function handleAnalyzeGaps() {
    setError(null);
    setLoading(true);
    try {
      const result = await analyzeSkills(resumeText, jobDescription);
      setSkillAnalysis(result);
      setCurrentQuestionIndex(0);
      setSkillAnswers([]);
      if (result.allResolved || result.questions.length === 0) {
        setAppState('ready');
      } else {
        setAppState('questioning');
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Skill analysis failed.');
    } finally {
      setLoading(false);
    }
  }

  // ─── Answer skill gap question ─────────────────────────────────────────────
  const handleAnswer = useCallback(
    (answer: SkillAnswer) => {
      const updated = [...skillAnswers, answer];
      setSkillAnswers(updated);

      const nextIndex = currentQuestionIndex + 1;
      if (!skillAnalysis || nextIndex >= skillAnalysis.questions.length) {
        setAppState('ready');
      } else {
        setCurrentQuestionIndex(nextIndex);
      }
    },
    [skillAnswers, currentQuestionIndex, skillAnalysis],
  );

  // ─── Regenerate — go back to ready so user can pick what to generate next ──
  function handleRegenerate() {
    setAppState('ready');
  }

  // ─── Generate document ─────────────────────────────────────────────────────
  async function handleGenerate(type: DocumentType) {
    setError(null);
    setGeneratingType(type);
    setAppState('generating');
    setLoading(true);
    // Cover letters are always exported as DOCX; only resumes respect selectedFormat
    const format: ExportFormat = type === 'COVER_LETTER' ? 'DOCX' : selectedFormat;
    const prevCL = previousCoverLetter.trim() || undefined;
    try {
      if (type === 'RESUME') {
        const result = await generateResume(resumeText, jobDescription, format, skillAnswers, prevCL);
        setGeneratedResume(result);
      } else {
        const result = await generateCoverLetter(resumeText, jobDescription, format, skillAnswers, prevCL);
        setGeneratedCoverLetter(result);
      }
      setAppState('results');
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Generation failed.');
      setAppState('ready');
    } finally {
      setLoading(false);
    }
  }

  // ─── Render ────────────────────────────────────────────────────────────────
  if (!authChecked) return null;

  if (!userEmail) {
    return <AuthPage onAuth={handleAuth} />;
  }

  const canAnalyze = resumeText.trim().length > 0 && jobDescription.trim().length > 0;
  const isAnalyzing = appState === 'analyzing';

  return (
    <div className="app">
      <Header onLogout={handleLogout} userEmail={userEmail} />

      {error && (
        <div className="error-banner">
          <span>{error}</span>
          <button onClick={() => setError(null)} aria-label="Dismiss">✕</button>
        </div>
      )}

      <main className="workspace">
        <div className="workspace__left">
          <ResumeCard resumeText={resumeText} onResumeText={setResumeText} />
          <JobPostingCard
            jobDescription={jobDescription}
            onJobDescription={setJobDescription}
            onAnalyze={handleAnalyze}
            loading={isAnalyzing}
            canAnalyze={canAnalyze}
          />
          <CoverLetterCard value={previousCoverLetter} onChange={setPreviousCoverLetter} />
        </div>

        <div className="workspace__right">
          <RightPanel
            appState={appState}
            jobAnalysis={jobAnalysis}
            skillAnalysis={skillAnalysis}
            currentQuestionIndex={currentQuestionIndex}
            resume={generatedResume}
            coverLetter={generatedCoverLetter}
            selectedFormat={selectedFormat}
            onFormatChange={setSelectedFormat}
            onAnalyzeGaps={handleAnalyzeGaps}
            onAnswer={handleAnswer}
            onGenerate={handleGenerate}
            onRegenerate={handleRegenerate}
            loading={loading}
            generatingType={generatingType}
          />
        </div>
      </main>
    </div>
  );
}
