import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  template: `
    <header class="miso-header">
      <div class="miso-header-inner">
        <a routerLink="/enrollments" class="miso-logo">LES</a>
        <span class="miso-tagline">Locational Enrollment Service</span>
        <nav class="miso-nav">
          <a routerLink="/enrollments" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }">Enrollments</a>
          <a routerLink="/enrollments/new">New enrollment</a>
          <a routerLink="/admin/withdraw-rejections">Admin – Rejections</a>
        </nav>
      </div>
    </header>
    <main class="miso-main">
      <router-outlet></router-outlet>
    </main>
    <footer class="miso-footer">
      <div class="miso-footer-inner">MISO Energy – LES PoC</div>
    </footer>
  `,
  styles: [`
    .miso-header {
      background: var(--miso-primary);
      color: #fff;
      box-shadow: var(--shadow-md);
    }
    .miso-header-inner {
      max-width: 1200px;
      margin: 0 auto;
      padding: 0.75rem 1.5rem;
      display: flex;
      align-items: center;
      gap: 1rem;
      flex-wrap: wrap;
    }
    .miso-logo {
      font-weight: 700;
      font-size: 1.25rem;
      color: #fff;
      text-decoration: none;
    }
    .miso-tagline {
      font-size: 0.875rem;
      opacity: 0.9;
    }
    .miso-nav {
      margin-left: auto;
      display: flex;
      gap: 1rem;
    }
    .miso-nav a {
      color: rgba(255,255,255,0.9);
      text-decoration: none;
      font-size: 0.875rem;
      padding: 0.35rem 0.5rem;
      border-radius: 4px;
    }
    .miso-nav a:hover, .miso-nav a.active {
      color: #fff;
      background: rgba(255,255,255,0.15);
    }
    .miso-main {
      min-height: calc(100vh - 120px);
      padding: 1.5rem;
    }
    .miso-footer {
      background: var(--miso-primary-dark);
      color: rgba(255,255,255,0.7);
      font-size: 0.75rem;
      padding: 0.75rem 1.5rem;
    }
    .miso-footer-inner {
      max-width: 1200px;
      margin: 0 auto;
    }
  `],
})
export class AppComponent {}
