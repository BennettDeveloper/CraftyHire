interface ResumePreviewProps {
  map: Record<string, string>;
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

function get(map: Record<string, string>, key: string): string {
  const v = map[key] ?? '';
  return v === 'REMOVE' || v.trim() === '' ? '' : v.trim();
}

function getByPrefix(map: Record<string, string>, prefix: string): string {
  const key = Object.keys(map).find(k => k.startsWith(prefix)) ?? '';
  return key ? get(map, key) : '';
}

function ok(v: string): boolean {
  return v.trim() !== '';
}

/** Find all unique numeric indices where keys match a pattern like /^\[Foo (\d+)\]$/. */
function getIndices(map: Record<string, string>, pattern: RegExp): number[] {
  const set = new Set<number>();
  for (const key of Object.keys(map)) {
    const m = key.match(pattern);
    if (m) set.add(+m[1]);
  }
  return Array.from(set).sort((a, b) => a - b);
}

/**
 * Collect bullet text for a given section index.
 * keyPattern must have group 1 = section index, group 2 = bullet index.
 */
function getBullets(
  map: Record<string, string>,
  keyPattern: RegExp,
  sectionIndex: number,
): string[] {
  const bullets: { idx: number; text: string }[] = [];
  for (const [key, val] of Object.entries(map)) {
    const m = key.match(keyPattern);
    if (m && +m[1] === sectionIndex) {
      const text = val === 'REMOVE' ? '' : val.trim();
      if (text) bullets.push({ idx: +m[2], text });
    }
  }
  return bullets.sort((a, b) => a.idx - b.idx).map(b => b.text);
}

// ─── Skill row labels in display order ───────────────────────────────────────
const SKILL_ROWS = [
  { prefix: '[Languages:',           label: 'Languages' },
  { prefix: '[Frameworks:',          label: 'Frameworks' },
  { prefix: '[Databases:',           label: 'Databases' },
  { prefix: '[Tools:',               label: 'Tools & Platforms' },
  { prefix: '[Methodologies:',       label: 'Methodologies' },
  { prefix: '[Professional Skills:', label: 'Professional Skills' },
  { prefix: '[Certifications:',      label: 'Certifications' },
];

// ─── Component ───────────────────────────────────────────────────────────────

export default function ResumePreview({ map }: ResumePreviewProps) {
  // Header
  const name     = get(map, '[FIRST AND LAST NAME]');
  const city     = get(map, '[City, State]');
  const phone    = get(map, '[Phone Number]');
  const email    = get(map, '[Professional Email]');
  const linkedin = get(map, '[LinkedIn URL]');
  const github   = get(map, '[GitHub URL]');
  const contactParts = [city, phone, email, linkedin, github].filter(ok);

  // Professional Summary
  const summary = getByPrefix(map, '[Professional Summary:');

  // Skills
  const skills = SKILL_ROWS
    .map(s => ({ label: s.label, value: getByPrefix(map, s.prefix) }))
    .filter(s => ok(s.value));

  // Experience
  const jobIndices = getIndices(map, /^\[Job Title (\d+)\]$/);
  const jobs = jobIndices.map(n => ({
    title:    get(map, `[Job Title ${n}]`),
    company:  get(map, `[Company Name ${n}]`),
    location: get(map, `[City, State ${n}]`),
    start:    get(map, `[Month Year ${n} Start]`),
    end:      get(map, `[Month Year ${n} End]`),
    bullets:  getBullets(map, /^\[Job (\d+) Bullet (\d+):/, n),
  })).filter(j => ok(j.title) || ok(j.company));

  // Projects
  const projIndices = getIndices(map, /^\[Project Name (\d+)\]$/);
  const projects = projIndices.map(n => ({
    name:        get(map, `[Project Name ${n}]`),
    stack:       get(map, `[Tech Stack ${n}]`),
    githubLink:  get(map, `[GitHub Link ${n}]`),
    demoLink:    get(map, `[Live Demo Link ${n}]`),
    description: getByPrefix(map, `[Project ${n} Description:`),
    bullets:     getBullets(map, /^\[Project (\d+) Bullet (\d+):/, n),
  })).filter(p => ok(p.name));

  // Education
  const eduIndices = getIndices(map, /^\[Degree\/Program (\d+)\]$/);
  const educations = eduIndices.map(n => ({
    degree:      get(map, `[Degree/Program ${n}]`),
    institution: get(map, `[Institution ${n}]`),
    year:        get(map, `[Graduation Year ${n}]`),
    details:     getByPrefix(map, `[Education ${n} Details:`),
  })).filter(e => ok(e.degree) || ok(e.institution));

  return (
    <div className="resume-preview">

      {/* ── Name ─────────────────────────────────────────────────────────── */}
      {ok(name) && <h1 className="rp-name">{name}</h1>}

      {/* ── Contact line ─────────────────────────────────────────────────── */}
      {contactParts.length > 0 && (
        <p className="rp-contact">
          {contactParts.map((part, i) => (
            <span key={i}>
              {i > 0 && <span className="rp-sep"> | </span>}
              {part}
            </span>
          ))}
        </p>
      )}

      {/* ── Professional Summary ─────────────────────────────────────────── */}
      {ok(summary) && (
        <section className="rp-section">
          <h2 className="rp-section-title">Professional Summary</h2>
          <p className="rp-summary">{summary}</p>
        </section>
      )}

      {/* ── Skills & Certifications ──────────────────────────────────────── */}
      {skills.length > 0 && (
        <section className="rp-section">
          <h2 className="rp-section-title">Skills &amp; Certifications</h2>
          <div className="rp-skills">
            {skills.map(s => (
              <p key={s.label} className="rp-skill-line">
                <strong className="rp-skill-label">{s.label}:</strong> {s.value}
              </p>
            ))}
          </div>
        </section>
      )}

      {/* ── Experience ───────────────────────────────────────────────────── */}
      {jobs.length > 0 && (
        <section className="rp-section">
          <h2 className="rp-section-title">Experience</h2>
          {jobs.map((job, i) => (
            <div key={i} className="rp-entry">
              <div className="rp-entry-row">
                <div className="rp-entry-left">
                  {ok(job.title)   && <span className="rp-entry-title">{job.title}</span>}
                  {ok(job.company) && (
                    <span className="rp-entry-org">
                      {job.company}{ok(job.location) ? `, ${job.location}` : ''}
                    </span>
                  )}
                </div>
                {(ok(job.start) || ok(job.end)) && (
                  <span className="rp-entry-dates">
                    {job.start}{ok(job.start) && ok(job.end) ? ' – ' : ''}{job.end}
                  </span>
                )}
              </div>
              {job.bullets.length > 0 && (
                <ul className="rp-bullets">
                  {job.bullets.map((b, bi) => <li key={bi}>{b}</li>)}
                </ul>
              )}
            </div>
          ))}
        </section>
      )}

      {/* ── Projects ─────────────────────────────────────────────────────── */}
      {projects.length > 0 && (
        <section className="rp-section">
          <h2 className="rp-section-title">Projects</h2>
          {projects.map((proj, i) => (
            <div key={i} className="rp-entry">
              <div className="rp-entry-row">
                <div className="rp-entry-left">
                  {ok(proj.name)  && <span className="rp-entry-title">{proj.name}</span>}
                  {ok(proj.stack) && <span className="rp-entry-org">{proj.stack}</span>}
                </div>
                {(ok(proj.githubLink) || ok(proj.demoLink)) && (
                  <span className="rp-entry-dates rp-entry-links">
                    {[proj.githubLink, proj.demoLink].filter(ok).join(' · ')}
                  </span>
                )}
              </div>
              {ok(proj.description) && (
                <p className="rp-proj-desc">{proj.description}</p>
              )}
              {proj.bullets.length > 0 && (
                <ul className="rp-bullets">
                  {proj.bullets.map((b, bi) => <li key={bi}>{b}</li>)}
                </ul>
              )}
            </div>
          ))}
        </section>
      )}

      {/* ── Education ────────────────────────────────────────────────────── */}
      {educations.length > 0 && (
        <section className="rp-section">
          <h2 className="rp-section-title">Education</h2>
          {educations.map((edu, i) => (
            <div key={i} className="rp-entry">
              <div className="rp-entry-row">
                <div className="rp-entry-left">
                  {ok(edu.degree)      && <span className="rp-entry-title">{edu.degree}</span>}
                  {ok(edu.institution) && <span className="rp-entry-org">{edu.institution}</span>}
                </div>
                {ok(edu.year) && (
                  <span className="rp-entry-dates">{edu.year}</span>
                )}
              </div>
              {ok(edu.details) && (
                <p className="rp-proj-desc">{edu.details}</p>
              )}
            </div>
          ))}
        </section>
      )}

    </div>
  );
}
