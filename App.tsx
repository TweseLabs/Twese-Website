/**
 * Design system: Clinical Field Notes — disciplined, institutional routing for TWESE INC.
 * Pages remain factual and have visible paths back to the core public record.
 */
import { Toaster } from "@/components/ui/sonner";
import { TooltipProvider } from "@/components/ui/tooltip";
import NotFound from "@/pages/NotFound";
import Clinic from "@/pages/Clinic";
import Home from "@/pages/Home";
import Impact from "@/pages/Impact";
import Labs from "@/pages/Labs";
import Partner from "@/pages/Partner";
import Team from "@/pages/Team";
import { Route, Switch } from "wouter";
import ErrorBoundary from "./components/ErrorBoundary";
import { ThemeProvider } from "./contexts/ThemeContext";

function Router() {
  return (
    <Switch>
      <Route path="/" component={Home} />
      <Route path="/clinic" component={Clinic} />
      <Route path="/labs" component={Labs} />
      <Route path="/impact" component={Impact} />
      <Route path="/team" component={Team} />
      <Route path="/partner" component={Partner} />
      <Route component={NotFound} />
    </Switch>
  );
}

export default function App() {
  return (
    <ErrorBoundary>
      <ThemeProvider defaultTheme="light">
        <TooltipProvider><Toaster /><Router /></TooltipProvider>
      </ThemeProvider>
    </ErrorBoundary>
  );
}
