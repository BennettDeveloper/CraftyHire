// ─── Auth ────────────────────────────────────────────────────────────────────

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  email: string;
  role: string;
}

// ─── Resume ──────────────────────────────────────────────────────────────────

export interface ParseResumeResponse {
  resumeText: string;
  fileType: string;
}

export interface SkillScore {
  skillName: string;
  relevanceScore: number; // 0.0 – 1.0
  category: string;
}

export interface JobAnalysisResponse {
  analysis: string;
  skills: SkillScore[];
}

export interface GenerateDocumentResponse {
  content: string;
  documentType: 'RESUME' | 'COVER_LETTER';
}

// ─── Skills ──────────────────────────────────────────────────────────────────

export interface SkillGap {
  skillName: string;
  relevanceScore: number;
  hasExperience: boolean;
  transferableExperience: string;
  resolved: boolean;
}

export interface Question {
  skillName: string;
  questionText: string;
  questionType: 'YES_NO' | 'OPEN_ENDED';
}

export interface SkillAnswer {
  skillName: string;
  hasExperience: boolean;
  description: string;
}

export interface SkillAnalysisResponse {
  skillGaps: SkillGap[];
  questions: Question[];
  allResolved: boolean;
}

export interface SkillAnswerResponse {
  updatedGap: SkillGap;
  resolved: boolean;
}

// ─── App State ───────────────────────────────────────────────────────────────

export type AppState =
  | 'idle'
  | 'analyzing'
  | 'skill_results'
  | 'questioning'
  | 'ready'
  | 'generating'
  | 'results';

export type ExportFormat = 'PDF' | 'DOCX' | 'LATEX';
export type DocumentType = 'RESUME' | 'COVER_LETTER';
