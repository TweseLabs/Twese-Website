/**
 * Style: an institutional portrait directory with equal visual dignity for all co-founders. Confirmed
 * public names, roles, and modest verified bios are preserved; initials stand in only where no approved portrait exists.
 */
import { ArrowRight } from "lucide-react";
import { Link } from "wouter";
import SiteFrame from "@/components/SiteFrame";

type Member = { initials: string; name: string; role: string; photo?: string; alt?: string; position?: string; bio?: string };
const founders: Member[] = [
  { initials: "JN", name: "Dr. Joel Nibigira, MD", role: "Co-Founder & Medical Director", bio: "Dr. Nibigira leads Twese Clinic’s clinical operations in Burundi. His work spans care delivery, clinical protocols, chronic-disease management, community-health coordination, and the translation of Twese’s research initiatives into the realities of patient care in Kabezi." },
  { initials: "CN", name: "Coca Ndacayisaba", role: "Co-Founder & Operations Director", photo: "/manus-storage/CocaNdacayisaba_51872be2.webp", alt: "Coca Ndacayisaba", position: "50% 24%", bio: "Coca leads day-to-day operations in Kabezi, coordinating clinic logistics, staffing, Community Health Worker activity, supplies, field operations, and the practical systems required to keep Twese’s clinical work functioning." },
  { initials: "EM", name: "Eloi Mugabe Bigirimana, ScM", role: "Co-Founder & Director, Twese Labs", photo: "/manus-storage/EloiMugabeBigirimana_684973bb.webp", alt: "Eloi Mugabe Bigirimana", position: "50% 25%", bio: "Eloi leads Twese Labs and the organization’s research and technology strategy. His work focuses on health-system design, biomedical engineering, and the development of AccessMR, linking clinical constraints identified in Kabezi with technical research aimed at expanding diagnostic capability in low-resource settings." },
];
const clinicalTeam: Member[] = [
  { initials: "JA", name: "Dr. J. Arnelas Irakaza, MD", role: "MD", photo: "/manus-storage/Dr.JArnelas_1dc8a8a2.webp", alt: "Dr. J. Arnelas Irakaza", position: "50% 22%" },
  { initials: "EN", name: "Eric Nyandui, NP", role: "NP", photo: "/manus-storage/EricNyandui_b9fa8ecf.webp", alt: "Eric Nyandui", position: "50% 28%" },
  { initials: "EM", name: "Ella Murekerisoni", role: "Nurse", photo: "/manus-storage/ellamurekerisoni_70e14124.webp", alt: "Ella Murekerisoni", position: "50% 24%" },
  { initials: "EN", name: "Evelyn Ndayisaba", role: "Nurse", photo: "/manus-storage/EvelynNdayisaba_3eba14b7.webp", alt: "Evelyn Ndayisaba", position: "50% 24%" },
  { initials: "EN", name: "Evelyne Ninkunda", role: "Nurse", photo: "/manus-storage/EvelyneNinkunda_60eb668d.webp", alt: "Evelyne Ninkunda", position: "50% 25%" },
  { initials: "F", name: "Ferdinand", role: "Grounds Manager", photo: "/manus-storage/FerdinandHavyarimana_563b69b9.webp", alt: "Ferdinand", position: "50% 25%" },
];
const researchTeam: Member[] = [{ initials: "JL", name: "Jake Lally", role: "Senior Research Development Associate" }, { initials: "ZH", name: "Zoe Hughes", role: "Research Associate" }];
function Portrait({ member, compact = false }: { member: Member; compact?: boolean }) { return <article className={compact ? "team-staff" : "team-lead"}>{member.photo ? <img src={member.photo} alt={member.alt} style={{ objectPosition: member.position }} /> : <div className="team-initials" aria-label={`${member.name} initials`}>{member.initials}</div>}<div><h3>{member.name}</h3>{member.role && <p>{member.role}</p>}{member.bio && <span>{member.bio}</span>}</div></article>; }

export default function Team() {
  return <SiteFrame>
    <section className="team-hero"><div className="container"><p className="page-kicker">Our team</p><h1>The people building Twese</h1><p>Twese operates through clinical, operations, and research teams supporting care and practical research in Kabezi, Bujumbura Province, Burundi.</p></div></section>
    <section className="team-leadership"><div className="container"><div className="team-section-heading"><div><p className="page-kicker">Leadership</p><h2>Clinical leadership and operations.</h2></div><p>Three co-founders guide Twese’s care, operations, and research work.</p></div><div className="team-leadership__grid">{founders.map(member => <Portrait key={member.name} member={member} />)}</div></div></section>
    <section className="team-clinical"><div className="container"><div className="team-clinical__image"><img src="/manus-storage/Meettheteampicturegap_c13d9462.webp" alt="Twese Clinic staff reviewing supplies during clinic operations" /></div><div className="team-section-heading"><div><p className="page-kicker">Clinical team</p><h2>Care delivery at the Kabezi site.</h2></div><p>The clinical team provides primary, maternal, child, and chronic disease care at the Kabezi site.</p></div><div className="team-staff__grid">{clinicalTeam.map((member, index) => <Portrait key={`${member.name}-${index}`} member={member} compact />)}</div></div></section>
    <section className="team-research"><div className="container"><div className="team-section-heading"><div><p className="page-kicker">Research & development</p><h2>Technical and academic work.</h2></div><p>This team supports Twese’s research agenda, technical development, and academic collaborations.</p></div><div className="team-research__grid">{researchTeam.map(member => <Portrait key={member.name} member={member} compact />)}</div></div></section>
    <section className="team-end"><div className="container team-end__grid"><div><p className="eyebrow eyebrow--light">Collaborate with Twese</p><h2>Long-term, community-grounded work.</h2></div><Link href="/partner" className="button button--outline-light">Get in touch <ArrowRight size={16} /></Link></div></section>
  </SiteFrame>;
}
