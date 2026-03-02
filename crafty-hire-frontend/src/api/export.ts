import type { ExportFormat, DocumentType } from '../types';
import { apiBlob } from './client';

const ENDPOINT: Record<ExportFormat, string> = {
  PDF: '/api/export/pdf',
  DOCX: '/api/export/word',
  LATEX: '/api/export/latex',
};

const FILENAME: Record<ExportFormat, Record<DocumentType, string>> = {
  PDF: { RESUME: 'resume.pdf', COVER_LETTER: 'cover_letter.pdf' },
  DOCX: { RESUME: 'resume.docx', COVER_LETTER: 'cover_letter.docx' },
  LATEX: { RESUME: 'resume.tex', COVER_LETTER: 'cover_letter.tex' },
};

export async function exportDocument(
  content: string,
  format: ExportFormat,
  documentType: DocumentType,
): Promise<void> {
  const blob = await apiBlob(ENDPOINT[format], {
    method: 'POST',
    body: JSON.stringify({ content, format, documentType }),
  });

  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = FILENAME[format][documentType];
  a.click();
  URL.revokeObjectURL(url);
}
