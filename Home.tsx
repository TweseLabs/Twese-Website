/**
 * Home-only recovery composition: photography → verified metrics → division photography → editorial statement → collaboration → CTA.
 * All photography is Twese-supplied, displayed without alteration, and distinct by placement.
 */
import { ArrowRight, ArrowUpRight } from "lucide-react";
import { Link } from "wouter";
import SiteFrame, { LogoLockup } from "@/components/SiteFrame";

const photos = {
  hero: "/manus-storage/home-community-session_2cabcf37.webp",
  clinic: "/manus-storage/clinic-care-delivery_112b5ee6.jpg",
  labs: "/manus-storage/home-labs-diagnostics_a4183d78.jpg",
  approach: "/manus-storage/home-approach-clinic-activity_c42596d5.jpg",
};
const tickerEntries = [
  "International Society for Magnetic Resonance in Medicine",
  "American Medical Student Association",
  "Harvard African Healthcare Conference",
  "Spring Communities",
];
const recentWork = [
  ["2026", "ISMRM/ISMRI", "AccessMR abstract accepted and published for ISMRM/ISMRI 2026, Cape Town."],
  ["2026", "Harvard African Healthcare Conference", "Second place, abstract competition."],
  ["2025", "AMSA", "AccessMR selected as an honoree."],
];

export default function Home() {
  return <SiteFrame>
    <section className="home-hero home-hero--shared-media" style={{ backgroundImage: `url(${photos.hero})` }}><div className="container home-hero__grid"><div className="home-hero__copy"><p className="eyebrow eyebrow--light">TWESE INC</p><h1>Building sustainable<br />health systems in<br /><span>rural Burundi.</span></h1><p>Twese Clinic delivers community-based primary care in Kabezi, Bujumbura Province, Burundi. <strong>Twese Labs</strong> supports practical medical technology development for low-resource settings.</p><div className="action-row"><Link className="button button--orange" href="/impact">View Impact <ArrowRight size={15} /></Link><Link className="button button--outline-light" href="/clinic">Explore Twese Clinic <ArrowUpRight size={14} /></Link><Link className="button button--outline-light" href="/labs">Research & Technology <ArrowUpRight size={14} /></Link></div></div></div></section>

    <section className="home-metrics" aria-label="Verified Twese operating metrics"><div className="container home-metrics__grid"><article><p className="home-metrics__value">Operating since 2023</p><p className="home-metrics__label">Kabezi, Bujumbura Province, Burundi</p></article><article><p className="home-metrics__value">$0</p><p className="home-metrics__label">Average Cost to Patients<br />Free at point of care</p></article><article><p className="home-metrics__value">Community-Based Primary Care</p><p className="home-metrics__label">Twese Clinic<br />Kabezi, Burundi</p></article></div></section>

    <section className="home-divisions section-pad"><div className="container home-divisions__grid"><article className="division-feature"><figure><img src={photos.clinic} alt="Twese Clinic team member providing care during a clinic visit" /></figure><div className="division-feature__content"><LogoLockup division="clinic" /><h2>Primary care built around rural communities</h2><p>Twese Clinic provides community-based primary and preventive care from its site in Kabezi. Care is designed around practical barriers to access, continuity, and affordability in a rural setting.</p><Link href="/clinic" className="button button--navy">Explore Twese Clinic <ArrowRight size={15} /></Link></div></article><article className="division-feature"><figure><img src={photos.labs} alt="Twese staff reviewing diagnostic equipment during an operational planning visit" /></figure><div className="division-feature__content"><LogoLockup division="labs" /><h2>Medical technology for low-resource settings</h2><p>Twese Labs supports practical research and medical technology development focused on challenges encountered in resource-constrained health systems.</p><Link href="/labs" className="button button--outline-navy">Explore Twese Labs <ArrowRight size={15} /></Link></div></article></div></section>

    <section className="home-approach" style={{ backgroundImage: `url(${photos.approach})` }}><div className="container home-approach__content"><p className="eyebrow eyebrow--light">Our approach</p><blockquote>“Sustainable healthcare requires systems designed around continuity, affordability, and the realities of the communities they serve.”</blockquote></div></section>

    <section className="research-ticker" aria-label="Research, recognition and field connections"><div className="container research-ticker__grid"><p className="research-ticker__label"><span aria-hidden="true" />Research, recognition<br />&amp; field connections</p><div className="research-ticker__viewport" tabIndex={0}><p className="sr-only">Selected organizations connected to Twese’s research, recognition, and field work: {tickerEntries.join(", ")}.</p><div className="research-ticker__track" aria-hidden="true">{[...tickerEntries, ...tickerEntries].map((entry, index) => <span key={`${entry}-${index}`}>{entry}<i /></span>)}</div></div><p className="research-ticker__note">Selected institutions, conferences, and organizations connected to Twese’s research, recognition, and field work.</p></div></section>

    <section className="recent-work" aria-labelledby="recent-work-title"><div className="container recent-work__grid"><div className="recent-work__intro"><p className="eyebrow"><span className="section-heading__rule" aria-hidden="true" />Research record</p><h2 id="recent-work-title">Recent Work</h2><Link href="/labs" className="recent-work__link">View all research &amp; recognition <ArrowRight size={14} /></Link></div><div className="recent-work__records">{recentWork.map(([year, context, item]) => <article key={`${year}-${context}`}><p>{year}</p><h3>{context}</h3><span>{item}</span></article>)}</div></div></section>

    <section className="home-collaboration-support section-pad"><div className="container"><div className="home-collaboration-support__intro"><p className="page-kicker"><span className="section-heading__rule" aria-hidden="true" />Collaboration &amp; Support</p><div><h2>Collaboration &amp; Support</h2><p>We work with organizations and individuals whose expertise can strengthen sustained care, research, and appropriate technology development.</p></div></div><div className="home-collaboration-support__grid"><article><p>01</p><h3>Academic Collaborators</h3><p>We partner with medical schools, public health programs, and research institutions for joint research, student placements, and co-authored publications.</p></article><article><p>02</p><h3>Philanthropic Funders</h3><p>We work with foundations and impact investors who prioritize measurable health outcomes and sustainable, community-grounded models of care.</p></article><article><p>03</p><h3>Engineering Collaborators</h3><p>Biomedical engineers, medical physicists, and embedded systems developers contributing to the AccessMR initiative and Twese Labs technology programs.</p></article></div></div></section>

    <section className="home-collab-cta"><div className="container home-collab-cta__grid"><div><p className="page-kicker">Collaboration</p><h2>Interested in collaborating or supporting our work?</h2></div><div><p>We are actively seeking academic partners, engineering collaborators, and philanthropic funders aligned with our mission to build sustainable health infrastructure in rural Burundi.</p><Link href="/partner" className="button button--navy">Partner With Us <ArrowRight size={15} /></Link></div></div></section>
  </SiteFrame>;
}
