/**
 * Recovered Twese visual system: white editorial surfaces, two-level navy fields, compact Lora headings,
 * muted DM Sans supporting text, and original division logos unchanged beneath a quiet TWESE INC umbrella stamp.
 */
import type { ReactNode } from "react";
import { useState } from "react";
import { Link, useLocation } from "wouter";
import { Menu, X } from "lucide-react";

const clinicLogo = "/manus-storage/TweseClinicLogo_359a2393.png";
const labsLogo = "/manus-storage/TweseLabsLogo_f1594ba0.JPEG";

export const navItems = [
  { label: "Impact", href: "/impact" },
  { label: "Twese Clinic", href: "/clinic" },
  { label: "Twese Labs", href: "/labs" },
  { label: "Partner With Us", href: "/partner" },
  { label: "Our Team", href: "/team" },
];

function NavLink({ href, label, onNavigate }: { href: string; label: string; onNavigate?: () => void }) {
  const [location] = useLocation();
  const selected = location === href;
  return <Link href={href} className={`site-nav__link ${selected ? "site-nav__link--active" : ""}`} aria-current={selected ? "page" : undefined} onClick={onNavigate}>{label}</Link>;
}

function Footer() {
  return <footer className="site-footer">
    <div className="site-footer__top container">
      <div className="site-footer__identity"><p className="site-footer__wordmark">TWESE INC</p><p className="site-footer__statement">Building sustainable health systems in rural Burundi.</p></div>
      <div className="site-footer__links"><div><p className="footer-label">Explore</p><Link href="/impact">Impact</Link><Link href="/clinic">Twese Clinic</Link><Link href="/labs">Twese Labs</Link></div><div><p className="footer-label">Institution</p><Link href="/partner">Partner With Us</Link><Link href="/team">Our Team</Link></div></div>
    </div>
    <div className="site-footer__brandline container"><p>Kabezi, Bujumbura Province, Burundi</p><p>© {new Date().getFullYear()} TWESE INC</p></div>
    <div className="site-footer__logos container" aria-label="Twese divisions"><img src={clinicLogo} alt="Twese Clinic" /><span className="site-footer__logo-rule" aria-hidden="true" /><img src={labsLogo} alt="Twese Labs" /></div>
  </footer>;
}

export default function SiteFrame({ children }: { children: ReactNode }) {
  const [menuOpen, setMenuOpen] = useState(false);
  const closeMenu = () => { setMenuOpen(false); window.scrollTo({ top: 0, behavior: "smooth" }); };
  return <div className="site-shell">
    <a className="skip-link" href="#main-content">Skip to main content</a>
    <header className="site-header"><div className="site-header__bar container">
      <Link href="/" className="header-logo" aria-label="Twese Inc home"><img src={clinicLogo} alt="Twese Clinic" /><span className="header-logo__umbrella">TWESE INC<i aria-hidden="true" /></span></Link>
      <nav className="site-nav" aria-label="Primary navigation">{navItems.map((item) => <NavLink key={item.href} {...item} />)}<Link href="/partner" className="nav-cta">Partner With Us</Link></nav>
      <button className="menu-button" type="button" aria-label={menuOpen ? "Close navigation" : "Open navigation"} aria-expanded={menuOpen} onClick={() => setMenuOpen(!menuOpen)}>{menuOpen ? <X size={20} /> : <Menu size={20} />}</button>
    </div>{menuOpen && <nav className="mobile-nav" aria-label="Mobile navigation">{navItems.map((item) => <NavLink key={item.href} {...item} onNavigate={closeMenu} />)}<Link href="/partner" className="nav-cta" onClick={closeMenu}>Partner With Us</Link></nav>}</header>
    <main id="main-content">{children}</main><Footer />
  </div>;
}

export function PageHero({ eyebrow, title, intro, marker, className = "", backgroundImage }: { eyebrow: string; title: string; intro: string; marker: string; className?: string; backgroundImage?: string }) {
  return <section className={`page-hero ${className}`} style={backgroundImage ? { backgroundImage: `url(${backgroundImage})` } : undefined}><div className="container page-hero__grid"><div><p className="eyebrow eyebrow--light">{eyebrow}</p><h1>{title}</h1><p className="page-hero__intro">{intro}</p></div><p className="page-hero__marker">{marker}</p></div></section>;
}

export function SectionHeading({ eyebrow, title, description }: { eyebrow?: string; title: string; description?: string }) {
  return <div className="section-heading">{eyebrow && <p className="eyebrow"><span className="section-heading__rule" aria-hidden="true" />{eyebrow}</p>}<h2>{title}</h2>{description && <p>{description}</p>}</div>;
}

export function ImagePlaceholder({ label, variant = "wide" }: { label: string; variant?: "wide" | "tall" | "square" }) {
  return <figure className={`image-placeholder image-placeholder--${variant}`} aria-label={`${label}; Twese photography required`}><div className="image-placeholder__frame" aria-hidden="true" /><figcaption><span>Photography record</span>{label}<small>Source status: Twese-supplied action photograph pending</small></figcaption></figure>;
}

export const LogoLockup = ({ division }: { division: "clinic" | "labs" }) => <img className={`division-logo division-logo--${division}`} src={division === "clinic" ? clinicLogo : labsLogo} alt={division === "clinic" ? "Twese Clinic" : "Twese Labs"} />;
