/** Design system: Clinical Field Notes — concise, legible recovery route for unavailable pages. */
import { ArrowRight } from "lucide-react";
import { Link } from "wouter";
import SiteFrame from "@/components/SiteFrame";

export default function NotFound() {
  return <SiteFrame><section className="not-found"><div className="container"><p className="eyebrow">404</p><h1>This page is not in the current public record.</h1><p>Use the primary navigation to return to the Twese Inc website.</p><Link className="button button--primary" href="/">Return home <ArrowRight size={17} /></Link></div></section></SiteFrame>;
}
