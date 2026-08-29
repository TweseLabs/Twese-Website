/**
 * Style: a clinical, technical, and institutional collaboration route with defined pathways and
 * appropriately reviewed work. It omits unsupplied direct contact details and donor-oriented framing.
 */
import { ArrowRight } from "lucide-react";
import type { FormEvent } from "react";
import { useState } from "react";
import SiteFrame, { PageHero } from "@/components/SiteFrame";

const pathways = [["Academic collaboration", "Research in imaging, health systems, implementation science, biomedical engineering, and related fields."], ["Engineering collaboration", "Technical work in low-field MRI, hardware, RF, magnet systems, reconstruction, and infrastructure-aware design."], ["Philanthropic funding", "Support for defined clinical operations, research development, equipment, and specific projects."], ["Institutional partnerships", "Universities, hospitals, laboratories, research groups, and aligned organizations."]];
const principles = [["Clearly scoped", "Each collaboration should have a defined objective, role, and deliverable."], ["Operationally grounded", "Projects must account for the clinical and infrastructure realities of the Kabezi setting."], ["Appropriately reviewed", "Clinical and human-subject research proceeds through appropriate institutional and ethics review."]];

export default function Partner() {
  const [submitted, setSubmitted] = useState(false);
  const onSubmit = (event: FormEvent<HTMLFormElement>) => { event.preventDefault(); setSubmitted(true); };
  return <SiteFrame>
    <PageHero eyebrow="Partnership" title="Partner with us" intro="Twese works with hospitals, universities, clinicians, engineers, research groups, and aligned funders on defined clinical, technical, and implementation work." marker="" className="page-hero--documentary" backgroundImage="/manus-storage/june11-01c_32d6407c.jpg" />
    <section className="partner-intro"><div className="container partner-intro__grid"><div><p className="page-kicker">Collaboration</p><h2>Defined collaborations built around real operating needs.</h2><p>Partnerships are structured around specific clinical, research, engineering, or institutional priorities in Kabezi and Twese Labs.</p></div><figure className="document-photo partner-intro__image"><img src="/manus-storage/home-clinic-exterior_e898c6fd.jpg" alt="Twese Clinic exterior" /></figure></div></section>
    <section className="partner-pathways"><div className="container"><p className="page-kicker">Ways to collaborate</p><div className="partner-pathways__grid">{pathways.map(([title, copy], index) => <article key={title}><span>{String(index + 1).padStart(2, "0")}</span><h3>{title}</h3><p>{copy}</p></article>)}</div></div></section>
    <section className="partner-principles"><div className="container"><p className="page-kicker">What partnership looks like</p><div className="partner-principles__grid">{principles.map(([title, copy], index) => <article key={title}><span>{String(index + 1).padStart(2, "0")}</span><h3>{title}</h3><p>{copy}</p></article>)}</div></div></section>
    <section className="partner-contact"><div className="container partner-contact__grid"><div className="partner-contact__intro" style={{ backgroundImage: "url(/manus-storage/june11-03a_5cdbe571.jpg)" }}><div><p className="page-kicker">Contact</p><h2>Start a focused conversation.</h2><p>Please include the nature of your inquiry, organization, and area of potential collaboration.</p></div></div><form className="contact-form" onSubmit={onSubmit}><label><span>Name</span><input required name="name" autoComplete="name" /></label><label><span>Organization</span><input name="organization" autoComplete="organization" /></label><label><span>Email</span><input required type="email" name="email" autoComplete="email" /></label><label><span>Area of interest</span><select name="area"><option>Academic collaboration</option><option>Engineering collaboration</option><option>Philanthropic funding</option><option>Institutional partnerships</option><option>Other</option></select></label><label className="contact-form__full"><span>Message</span><textarea required name="message" rows={5} /></label><button className="button button--navy contact-form__submit" type="submit">Send inquiry <ArrowRight size={16} /></button>{submitted && <p className="form-disclosure" role="status">Thank you for your interest.</p>}</form></div></section>
  </SiteFrame>;
}
