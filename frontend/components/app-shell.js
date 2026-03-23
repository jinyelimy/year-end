"use client";

import Link from "next/link";

export function TopBar({ right }) {
  return (
    <header className="topbar">
      <div className="container topbar-inner">
        <Link className="brand" href="/">
          <span className="brand-mark">ET</span>
          <span>Ligg-Tax Next</span>
        </Link>
        {right}
      </div>
    </header>
  );
}

export function DashboardShell({ children, right }) {
  return (
    <div className="page-shell">
      <TopBar right={right} />
      <main className="container" style={{ padding: "28px 0 40px" }}>
        {children}
      </main>
    </div>
  );
}

export function AuthCard({ children }) {
  return (
    <div className="page-shell">
      <TopBar right={<span className="muted">Next.js migration</span>} />
      <main
        className="container"
        style={{
          minHeight: "calc(100vh - 72px)",
          display: "grid",
          placeItems: "center",
          padding: "32px 0"
        }}
      >
        <div className="surface" style={{ width: "min(520px, 100%)", borderRadius: 28, padding: 28 }}>
          {children}
        </div>
      </main>
    </div>
  );
}

export function PlaceholderPage({ title, description, bullets }) {
  return (
    <DashboardShell right={<span className="muted">마이그레이션 준비됨</span>}>
      <section className="surface placeholder">
        <div className="eyebrow">Migration Placeholder</div>
        <h1 className="title" style={{ fontSize: 36 }}>{title}</h1>
        <p className="muted" style={{ margin: 0, fontSize: 16, lineHeight: 1.7 }}>
          {description}
        </p>
        <ul>
          {bullets.map((bullet) => (
            <li key={bullet}>{bullet}</li>
          ))}
        </ul>
      </section>
    </DashboardShell>
  );
}
