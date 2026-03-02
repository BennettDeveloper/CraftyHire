import { useState } from 'react';
import type { Question, SkillAnswer } from '../types';
import { answerSkillQuestion } from '../api/skills';
import { ApiError } from '../api/client';

interface QuestionFlowProps {
  questions: Question[];
  currentIndex: number;
  onAnswer: (answer: SkillAnswer) => void;
}

export default function QuestionFlow({ questions, currentIndex, onAnswer }: QuestionFlowProps) {
  const [openEndedText, setOpenEndedText] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const question = questions[currentIndex];
  if (!question) return null;

  const total = questions.length;
  const progress = ((currentIndex) / total) * 100;

  async function submit(hasExperience: boolean, description: string) {
    setError(null);
    setSubmitting(true);
    try {
      await answerSkillQuestion(question.skillName, hasExperience, description);
      const answer: SkillAnswer = {
        skillName: question.skillName,
        hasExperience,
        description,
      };
      setOpenEndedText('');
      onAnswer(answer);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Failed to submit answer.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="right-panel">
      <div className="right-panel__header">
        <svg className="right-panel__icon" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <circle cx="12" cy="12" r="10" />
          <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3" />
          <line x1="12" y1="17" x2="12.01" y2="17" />
        </svg>
        <h2 className="right-panel__title">Skill Gap Questions</h2>
      </div>

      {/* Progress bar */}
      <div className="progress-track">
        <div className="progress-fill" style={{ width: `${progress}%` }} />
      </div>
      <p className="progress-label">
        Question {currentIndex + 1} of {total}
      </p>

      {/* Skill badge */}
      <div className="question-skill-badge">{question.skillName}</div>

      {/* Question text */}
      <p className="question-text">{question.questionText}</p>

      {error && <p className="field-error">{error}</p>}

      {question.questionType === 'YES_NO' ? (
        <div className="yesno-row">
          <button
            className="btn btn--yes"
            onClick={() => submit(true, 'Yes')}
            disabled={submitting}
          >
            Yes
          </button>
          <button
            className="btn btn--no"
            onClick={() => submit(false, 'No')}
            disabled={submitting}
          >
            No
          </button>
        </div>
      ) : (
        <div>
          <textarea
            className="textarea"
            placeholder="Describe your experience or transferable skills..."
            value={openEndedText}
            onChange={(e) => setOpenEndedText(e.target.value)}
            rows={5}
          />
          <button
            className="btn btn--primary btn--full"
            onClick={() => submit(openEndedText.trim().length > 0, openEndedText.trim())}
            disabled={submitting || openEndedText.trim().length === 0}
            style={{ marginTop: '0.75rem' }}
          >
            {submitting ? 'Submitting...' : 'Submit Answer'}
          </button>
        </div>
      )}
    </div>
  );
}
