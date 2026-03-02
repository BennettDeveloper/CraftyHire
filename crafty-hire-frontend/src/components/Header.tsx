interface HeaderProps {
  onLogout: () => void;
  userEmail: string | null;
}

export default function Header({ onLogout, userEmail }: HeaderProps) {
  return (
    <header className="site-header">
      <div className="site-header__brand">
        <span className="site-header__icon">
          {/* Briefcase icon */}
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <rect x="2" y="7" width="20" height="14" rx="2" ry="2" />
            <path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16" />
          </svg>
        </span>
        <h1 className="site-header__title">Crafty Hire</h1>
        <span className="site-header__sparkle" aria-hidden="true">
          {/* Sparkle icon */}
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M12 2l2.4 7.4H22l-6.2 4.5 2.4 7.4L12 17l-6.2 4.3 2.4-7.4L2 9.4h7.6z" />
          </svg>
        </span>
      </div>
      <p className="site-header__subtitle">
        AI-powered resume and cover letter optimization for ATS systems
      </p>
      {userEmail && (
        <div className="site-header__user">
          <span className="site-header__email">{userEmail}</span>
          <button className="site-header__logout" onClick={onLogout}>
            Sign out
          </button>
        </div>
      )}
    </header>
  );
}
