<script lang="ts">
  import { cubicOut } from 'svelte/easing'
  import { fade, fly, slide } from 'svelte/transition'
  import Logo from '../components/Logo.svelte'
  import Toggle from '../components/Toggle.svelte'
  import { call, on } from './backend'
  import type { Banner, Snapshot, Status, Target } from './backend'

  let snap = $state<Snapshot | null>(null)
  let selectedKey = $state<string | null>(null)
  let version = $state<string | null>(null)
  let layout = $state<'separate' | 'main'>('separate')
  let status = $state<Status | null>(null)
  let busy = $state(false)
  let busyTitle = $state('')
  let confirmingRemove = $state(false)
  let banner = $state<Banner>({ visible: false })
  let lines = $state<string[]>([])
  let error = $state('')
  let logEl: HTMLElement | undefined = $state()

  const target = $derived(snap?.targets.find((t) => t.key === selectedKey) ?? null)

  const ICONS: Record<string, string> = {
    VANILLA: 'M12 2l9 5v10l-9 5-9-5V7zM12 12l9-5M12 12v10M12 12L3 7',
    PRISM: 'M12 3l9 9-9 9-9-9zM12 8l4 4-4 4-4-4z',
    POLYMC: 'M12 3l9 9-9 9-9-9zM12 8l4 4-4 4-4-4z',
    MODRINTH: 'M12 3a9 9 0 100 18 9 9 0 000-18zM8 12h8M12 8v8',
    CUSTOM: 'M3 6h6l2 2h10v11H3z',
  }
  const STATE_LABEL: Record<string, string> = { installed: 'Installed', update: 'Update available', none: 'Not installed', unsupported: 'Unsupported' }

  function fail(e: unknown) {
    error = e instanceof Error ? e.message : String(e)
  }

  /** Picks the version a place should start on: its own, or what the player last used if Relict has a build for it. */
  function chooseVersion(t: Target | null) {
    if (!t || !snap) return
    version = t.minecraft ? t.minecraft : (t.suggestedVersion ?? (version && snap.versions.includes(version) ? version : snap.versions[0] ?? null))
  }

  function apply(next: Snapshot) {
    const keep = selectedKey && next.targets.some((t) => t.key === selectedKey) ? selectedKey : null
    snap = next
    selectedKey = keep ?? next.selected ?? next.targets[0]?.key ?? null
    if (!keep) chooseVersion(snap.targets.find((t) => t.key === selectedKey) ?? null)
  }

  async function refresh() {
    if (!selectedKey) { status = null; return }
    try {
      status = await call<Status>('status', { target: selectedKey, version })
    } catch (e) { fail(e) }
  }

  async function load() {
    try {
      apply(await call<Snapshot>('init', {}))
      await refresh()
      call('updates.startup', {}).catch(() => {})
    } catch (e) { fail(e) }
  }
  load()

  function select(t: Target) {
    selectedKey = t.key
    confirmingRemove = false
    chooseVersion(t)
    refresh()
  }

  async function run(method: 'install' | 'uninstall') {
    confirmingRemove = false
    error = ''
    try {
      status = await call<Status>(method, { target: selectedKey, version, layout })
      apply(await call<Snapshot>('init', { selected: selectedKey }))
    } catch (e) { fail(e) }
  }

  async function rescan() {
    try { apply(await call<Snapshot>('rescan', { selected: selectedKey })); await refresh() } catch (e) { fail(e) }
  }
  async function browse() {
    try { apply(await call<Snapshot>('browse', { selected: selectedKey })); await refresh() } catch (e) { fail(e) }
  }
  function setAuto(value: boolean) {
    if (snap) snap.autoUpdate = value
    call('autoUpdate', { value }).catch(fail)
  }

  on<{ value: boolean; title?: string }>('busy', (p) => {
    busy = p.value
    busyTitle = p.title ?? ''
    if (!p.value) refresh()
  })
  on<{ text: string }>('log', (p) => {
    lines.push(p.text)
    queueMicrotask(() => logEl && (logEl.scrollTop = logEl.scrollHeight))
  })
  on<Banner>('banner', (p) => { banner = p })
  on<Snapshot>('snapshot', (p) => { apply(p); refresh() })

  const pill = $derived(
    !status ? null
    : !status.installed ? { text: 'Not installed', tone: 'none' }
    : status.needsUpdate ? { text: `v${status.installedVersion} · update available`, tone: 'warn' }
    : { text: `v${status.installedVersion} · up to date`, tone: 'good' },
  )
  const installLabel = $derived(status?.installed ? 'Update / Repair' : 'Install')
  const hasLog = $derived(lines.length > 0 || busy)
</script>

<div class="stage">
  <div class="window" class:ready={snap !== null}>
    <aside>
      <div class="brand">
        <Logo size={38} />
        <div>
          <div class="name">Relict <em>Client</em></div>
          <div class="release">INSTALLER {snap?.installerVersion ?? ''}</div>
        </div>
      </div>

      <h2 class="head">Launchers</h2>
      <div class="scroll">
        <ul>
          {#each snap?.targets ?? [] as t (t.key)}
            <li>
              <button class:active={t.key === selectedKey} onclick={() => select(t)} title={t.dir}>
                <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d={ICONS[t.kind] ?? ICONS.CUSTOM} /></svg>
                <span class="label">
                  <b>{t.kind === 'VANILLA' ? t.launcher : t.name}</b>
                  <small>{t.kind === 'VANILLA' ? 'Official launcher' : t.launcher}{t.minecraft ? ` · ${t.minecraft}` : ''}</small>
                </span>
                <i class="dot {t.state}" title={STATE_LABEL[t.state]}></i>
              </button>
            </li>
          {:else}
            <li class="none">No launcher was found.</li>
          {/each}
        </ul>
      </div>

      <h2 class="head">General</h2>
      <ul class="fixed">
        <li><button onclick={rescan} disabled={busy}>
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M20 12a8 8 0 11-2.4-5.7M20 4v5h-5" /></svg><span class="label"><b>Rescan</b></span>
        </button></li>
        <li><button onclick={browse} disabled={busy}>
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M3 6h6l2 2h10v11H3z" /></svg><span class="label"><b>Browse for a folder</b></span>
        </button></li>
        <li><button onclick={() => call('updates.check', {}).catch(fail)} disabled={busy}>
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 4v11M7 11l5 5 5-5M5 20h14" /></svg><span class="label"><b>Check for updates</b></span>
        </button></li>
      </ul>

      <div class="auto">
        <div><b>Update automatically</b><small>Installs newer Relict builds when you open this.</small></div>
        <Toggle checked={snap?.autoUpdate ?? true} label="Update automatically" onchange={setAuto} />
      </div>
    </aside>

    <main>
      <div class="page">
        {#if banner.visible}
          <div class="row banner on" transition:slide={{ duration: 260, easing: cubicOut }}>
            <div class="text">
              <div class="name">{banner.title}</div>
              <div class="desc">{banner.detail}</div>
            </div>
            {#if banner.updateAction}<button class="btn" disabled={busy} onclick={() => call('updates.apply', {}).catch(fail)}>Update Relict</button>{/if}
            {#if banner.installerAction}<button class="btn ghost" disabled={busy} onclick={() => call('updates.installer', {}).catch(fail)}>Get new installer</button>{/if}
          </div>
        {/if}

        {#if error}
          <div class="row error" role="alert" transition:slide={{ duration: 220, easing: cubicOut }}>
            <div class="text"><div class="name">Something went wrong</div><div class="desc">{error}</div></div>
            <button class="btn ghost" onclick={() => (error = '')}>Dismiss</button>
          </div>
        {/if}

        {#key selectedKey}
          <div class="content" in:fly={{ y: 14, duration: 280, delay: 70, easing: cubicOut }} out:fade={{ duration: 90 }}>
        <header class="top">
          <div>
            <h1><em>{target ? (target.kind === 'VANILLA' ? target.launcher : target.name) : 'Relict'}</em> Setup</h1>
            <p>
              {#if target}
                {target.kind === 'VANILLA' ? 'Official launcher' : target.launcher}{target.detail ? ` · ${target.detail}` : ''}
              {:else}Finds your launchers and sets up Fabric and Relict.{/if}
            </p>
          </div>
          {#if pill}{#key pill.text}<span class="pill {pill.tone}" in:fade={{ duration: 220 }}><i></i>{pill.text}</span>{/key}{/if}
        </header>

        {#if !target}
          <div class="empty">No launcher was found. Open a Minecraft launcher once, then press Rescan, or use Browse for a folder.</div>
        {:else}
          <div class="note" class:warn={status?.problem}>{status?.note ?? ' '}</div>

          {#if status?.pickVersion && snap}
            <section>
              <h2>Minecraft version</h2>
              <div class="chips">
                {#each snap.versions as v}
                  <button class="chip" class:active={version === v} aria-pressed={version === v} onclick={() => { version = v; refresh() }}>{v}</button>
                {/each}
              </div>
            </section>
          {/if}

          {#if status?.pickLayout}
            <section>
              <h2>Where to install</h2>
              <div class="choices">
                <button class="row choice" class:on={layout === 'separate'} aria-pressed={layout === 'separate'} onclick={() => (layout = 'separate')}>
                  <div class="text"><div class="name">Separate Relict instance <em>Recommended</em></div><div class="desc">Keeps your current mods, worlds and settings untouched.</div></div>
                  <span class="radio"></span>
                </button>
                <button class="row choice" class:on={layout === 'main'} aria-pressed={layout === 'main'} onclick={() => (layout = 'main')}>
                  <div class="text"><div class="name">Main Minecraft folder</div><div class="desc">Shares your existing mods. Older copies of Fabric API are moved aside, not deleted.</div></div>
                  <span class="radio"></span>
                </button>
              </div>
            </section>
          {/if}

          <div class="actions">
            <button class="btn big" disabled={busy || !status || status.blocked} onclick={() => run('install')}>{busy ? busyTitle || 'Working…' : installLabel}</button>
            {#if confirmingRemove}
              <button class="btn danger" disabled={busy} onclick={() => run('uninstall')}>Yes, remove Relict</button>
              <button class="btn ghost" onclick={() => (confirmingRemove = false)}>Cancel</button>
            {:else}
              <button class="btn ghost big" disabled={busy || !status || status.blocked || !status.installed} onclick={() => (confirmingRemove = true)}>Uninstall</button>
            {/if}
          </div>
          {#if confirmingRemove}<p class="muted small">This removes Relict from {target.label}. Your own worlds and mods are not touched.</p>{/if}

          {#if hasLog}
            <section class="activity" transition:slide={{ duration: 260, easing: cubicOut }}>
              <h2>Activity</h2>
              {#if busy}<div class="bar"><i></i></div>{/if}
              <div class="log" bind:this={logEl}>{#each lines as l}<div in:fly={{ x: -8, duration: 180 }}>{l}</div>{/each}</div>
            </section>
          {/if}
        {/if}
          </div>
        {/key}
      </div>
    </main>
  </div>
</div>

<style>
  :global(html), :global(body), :global(#app) { background: #04051a; }
  /* The window itself is the app: the page fills it edge to edge, with no frame of its own. */
  .stage { height: 100%; }
  .window { opacity: 0; transform: translateY(8px); transition: opacity 280ms ease, transform 380ms cubic-bezier(0.2, 0.9, 0.25, 1); }
  .window.ready { opacity: 1; transform: none; }
  .content { display: flex; flex-direction: column; gap: 18px; }
  .window {
    display: grid; grid-template-columns: 300px minmax(0, 1fr); height: 100%;
    background:
      radial-gradient(900px 520px at 0% 100%, rgba(80, 70, 220, 0.2), transparent 60%),
      linear-gradient(150deg, #0c0f36 0%, #04051a 62%);
  }

  aside {
    display: flex; flex-direction: column; min-height: 0; padding: 44px 16px 16px;
    border-right: 1px solid rgba(124, 130, 255, 0.14);
    background: linear-gradient(180deg, rgba(14, 16, 56, 0.5), rgba(6, 8, 34, 0.5));
  }
  .brand { display: flex; align-items: center; gap: 12px; padding: 0 8px 16px; }
  .name { font-family: var(--serif); font-size: 22px; line-height: 1.1; }
  .name em { font-style: italic; color: var(--accent-text); }
  .release { margin-top: 3px; font-size: 10.5px; letter-spacing: 0.06em; color: var(--faint); }
  .scroll { flex: 1; min-height: 60px; overflow-y: auto; padding-right: 2px; }
  h2.head { margin: 10px 10px 6px; }
  ul.fixed { flex: none; }
  ul { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 2px; }
  li.none { padding: 8px 12px; color: var(--muted); font-size: 13px; }
  li button {
    display: flex; align-items: center; gap: 12px; width: 100%; padding: 9px 12px; border-radius: 12px; text-align: left;
    color: #aeb1d2; border: 1px solid transparent; transition: background var(--ease), color var(--ease);
  }
  li button svg { flex: none; color: #8a8eb8; }
  li button .label { flex: 1; min-width: 0; display: flex; flex-direction: column; }
  li button .label b { font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  li button .label small { font-size: 11px; color: var(--faint); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  li button:hover:not(:disabled) { background: rgba(255, 255, 255, 0.04); color: var(--text); }
  li button:disabled { opacity: 0.4; cursor: default; }
  li button.active {
    color: #fff; border-color: rgba(135, 119, 255, 0.28);
    background: linear-gradient(90deg, rgba(135, 119, 255, 0.38), rgba(135, 119, 255, 0.12));
  }
  li button.active svg { color: var(--accent-text); }
  li button.active .label small { color: #c9c7f0; }
  .dot { flex: none; width: 8px; height: 8px; border-radius: 50%; background: transparent; }
  .dot.installed { background: var(--green); box-shadow: 0 0 8px rgba(62, 207, 142, 0.6); }
  .dot.update { background: var(--yellow); box-shadow: 0 0 8px rgba(245, 183, 61, 0.6); }
  .dot.unsupported { background: var(--faint); }

  .auto {
    display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-top: 10px; padding: 12px 12px 10px;
    border-top: 1px solid var(--border-soft);
  }
  .auto div { display: flex; flex-direction: column; gap: 2px; }
  .auto b { font-weight: 600; }
  .auto small { font-size: 11px; color: var(--muted); }

  main { min-width: 0; min-height: 0; overflow-y: auto; }
  .page { padding: 44px 34px 32px; max-width: 860px; display: flex; flex-direction: column; gap: 18px; }
  header.top { display: flex; flex-direction: row; align-items: flex-start; justify-content: space-between; gap: 18px; }
  header.top p { margin-top: 6px; color: var(--muted); }
  .pill {
    display: inline-flex; align-items: center; gap: 8px; flex: none; padding: 7px 14px; border-radius: 999px; font-size: 12.5px; font-weight: 600;
    background: #05061c; border: 1px solid var(--border); color: var(--muted);
  }
  .pill i { width: 7px; height: 7px; border-radius: 50%; background: currentColor; }
  .pill.good { color: var(--green); border-color: rgba(62, 207, 142, 0.35); }
  .pill.warn { color: var(--yellow); border-color: rgba(245, 183, 61, 0.35); }

  .row {
    position: relative; display: flex; align-items: center; gap: 14px; padding: 15px 18px 15px 22px; border-radius: 14px; overflow: hidden; text-align: left;
    background: linear-gradient(180deg, rgba(15, 18, 62, 0.6), rgba(9, 11, 42, 0.6));
    border: 1px solid var(--border); border-top-color: rgba(124, 130, 255, 0.24);
  }
  .row .text { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 3px; }
  .row .name { font-weight: 700; font-size: 15px; }
  .row .name em { font-style: normal; font-size: 10.5px; font-weight: 600; margin-left: 8px; padding: 2px 9px; border-radius: 999px; color: var(--accent-text); background: var(--accent-soft); }
  .row .desc { font-size: 12.5px; color: var(--muted); }
  .row.on { background: linear-gradient(180deg, rgba(26, 28, 90, 0.7), rgba(15, 17, 64, 0.7)); }
  .row.on::before { content: ''; position: absolute; left: 0; top: 14px; bottom: 14px; width: 3px; border-radius: 0 3px 3px 0; background: var(--accent); box-shadow: 0 0 14px 1px var(--accent-glow); }
  .row.error { border-color: rgba(255, 107, 107, 0.4); }
  .row.error .name { color: var(--danger); }

  .note { color: var(--green); font-size: 13px; }
  .note.warn { color: var(--yellow); }
  section { display: flex; flex-direction: column; gap: 10px; }
  .choices { display: flex; flex-direction: column; gap: 10px; }
  .choice { cursor: pointer; width: 100%; color: inherit; transition: border-color var(--ease); }
  .choice:hover { border-color: rgba(124, 130, 255, 0.35); }
  .radio { flex: none; width: 18px; height: 18px; border-radius: 50%; border: 2px solid var(--faint); transition: border-color var(--ease), background var(--ease), box-shadow var(--ease); }
  .choice.on .radio { border-color: var(--accent); background: radial-gradient(circle, var(--accent) 0 45%, transparent 50%); box-shadow: 0 0 12px var(--accent-glow); }

  .chips { display: flex; flex-wrap: wrap; gap: 8px; }
  .chip { padding: 7px 16px; border-radius: 999px; color: #b2b5d6; background: rgba(255, 255, 255, 0.04); border: 1px solid var(--border); transition: background var(--ease), color var(--ease), border-color var(--ease); }
  .chip:hover { color: var(--text); }
  .chip.active { color: #fff; background: var(--accent-soft); border-color: rgba(135, 119, 255, 0.5); }

  .actions { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; margin-top: 4px; }
  .btn:disabled { opacity: 0.4; cursor: default; filter: none; }
  .btn.big { padding: 12px 26px; min-width: 150px; border-radius: 12px; font-size: 15px; }
  .btn.danger { background: #d9534f; box-shadow: 0 6px 18px -8px rgba(217, 83, 79, 0.6); }
  .small { font-size: 12.5px; }

  .activity { margin-top: 4px; }
  .bar { height: 4px; border-radius: 4px; background: rgba(255, 255, 255, 0.06); overflow: hidden; }
  .bar i { display: block; height: 100%; width: 35%; border-radius: 4px; background: var(--accent); animation: slide 1.1s ease-in-out infinite; }
  @keyframes slide { 0% { transform: translateX(-100%); } 100% { transform: translateX(300%); } }
  .log {
    max-height: 150px; overflow-y: auto; padding: 12px 14px; border-radius: 12px; background: #05061c; border: 1px solid var(--border);
    font: 12px/1.6 ui-monospace, 'SF Mono', Menlo, Consolas, monospace; color: #aeb1d2; user-select: text;
  }
</style>
