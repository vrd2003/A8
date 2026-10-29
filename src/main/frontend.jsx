import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { createRoot } from "react-dom/client";

function overs(balls) {
  return `${Math.floor(balls / 6)}.${balls % 6}`;
}

function TeamLine({ team, match }) {
  const isBatting = team.code === match.battingTeam;
  const hasScore = team.balls > 0 || team.runs > 0 || team.wickets > 0;
  const showScore = hasScore || (isBatting && match.status !== "UPCOMING");
  return (
    <span className="team-row">
      <span className="team-identity">
        <span className="team-crest">{team.code}</span>
        <span className="team-name">{team.name}</span>
      </span>
      <span className="team-score">
        {showScore
          ? <>{team.runs}/{team.wickets}<span className="score-overs">({overs(team.balls)} ov)</span></>
          : <span className="score-overs">Yet to bat</span>}
      </span>
    </span>
  );
}

function MatchCard({ match, selected, onSelect }) {
  return (
    <button
      className={`match-card ${selected ? "selected" : ""}`}
      type="button"
      onClick={onSelect}
      aria-pressed={selected}
    >
      <span className="match-card-head">
        <span className="competition">{match.competition}</span>
        <span className={`status-pill status-${match.status.toLowerCase()}`}>
          {match.status === "LIVE" ? "● LIVE" : match.status}
        </span>
      </span>
      <TeamLine team={match.homeTeam} match={match} />
      <TeamLine team={match.awayTeam} match={match} />
      <span className="card-footer"><span>{match.summary}</span><span className="arrow">↗</span></span>
    </button>
  );
}

function MatchLineups({ match }) {
  return (
    <details className="lineup-details">
      <summary>Match lineups</summary>
      <p><strong>{match.battingTeam} batting:</strong> {match.battingLineup.join(", ")}</p>
      <p><strong>{match.bowlingTeam} bowling:</strong> {match.bowlingLineup.join(", ")}</p>
    </details>
  );
}

function Scorecard({ match, busy, onDelivery, onStart, onCreate }) {
  if (!match) {
    return (
      <article className="detail-card">
        <div className="empty-detail">
          <div>
            <strong>Your next match starts here.</strong>
            <p>Create a match to see its scorecard and live ball-by-ball updates.</p>
            <button className="create-button" type="button" onClick={onCreate}>+ Set up a match</button>
          </div>
        </div>
      </article>
    );
  }

  const batting = match.homeTeam.code === match.battingTeam ? match.homeTeam : match.awayTeam;
  const isLive = match.status === "LIVE";
  const recent = match.recentDeliveries.length
    ? match.recentDeliveries.map((ball, index) => {
      const className = ball.includes("W") ? "wicket" : ["4", "6"].includes(ball) ? "boundary" : "";
      return <span className={`ball ${className}`} key={`${match.revision}-${index}`}>{ball}</span>;
    })
    : <span className="ball empty">No deliveries recorded</span>;

  return (
    <article className="detail-card" aria-label={`${match.competition} scorecard`}>
      <div className="detail-top">
        <div>
          <p className="detail-competition">{match.competition}</p>
          <h3 className="detail-title">{match.homeTeam.name} <span className="versus">vs</span> {match.awayTeam.name}</h3>
          <p className="detail-venue">{match.venue}</p>
        </div>
        <div className="detail-live">{isLive && <i />} {isLive ? "LIVE" : match.status}</div>
      </div>
      <div className="score-panel">
        <div className="innings-label">
          <span>{isLive ? `1ST INNINGS · ${match.battingTeam} BATTING` : match.status}</span>
          <span className="batting-chip"><i />{match.battingTeam}</span>
        </div>
        <div className="big-score">
          <span className="big-score-code">{batting.code}</span>
          <span className="big-score-value">{batting.runs}/{batting.wickets}</span>
          <span className="big-score-overs">({overs(batting.balls)} ov)</span>
        </div>
        <p className="match-summary">{match.summary}</p>
        <div className="stat-strip">
          <div><span className="stat-label">RUN RATE</span><span className="stat-value">{batting.balls ? (batting.runs / (batting.balls / 6)).toFixed(2) : "—"}</span></div>
          <div><span className="stat-label">OVERS</span><span className="stat-value">{match.maxOvers}</span></div>
          <div><span className="stat-label">UPDATED</span><span className="stat-value">{new Date(match.updatedAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}</span></div>
        </div>
      </div>
      {isLive ? (
        <div className="scorecard-content">
          <div className="subhead">Batting <span>R&nbsp;&nbsp;&nbsp; B&nbsp; 4s&nbsp; 6s</span></div>
          <table className="stats-table">
            <thead><tr><th>BATTER</th><th>R</th><th>B</th><th>4s</th><th>6s</th></tr></thead>
            <tbody>
              <tr>
                <td>{match.striker} <span className="player-dismissal">● on strike</span></td>
                <td>{match.strikerStats.runs}</td><td>{match.strikerStats.balls}</td>
                <td>{match.strikerStats.fours}</td><td>{match.strikerStats.sixes}</td>
              </tr>
              <tr>
                <td>{match.nonStriker}</td>
                <td>{match.nonStrikerStats.runs}</td><td>{match.nonStrikerStats.balls}</td>
                <td>{match.nonStrikerStats.fours}</td><td>{match.nonStrikerStats.sixes}</td>
              </tr>
            </tbody>
          </table>
          <div className="subhead recent-head">Bowling <span>O&nbsp;&nbsp;&nbsp; R&nbsp; W</span></div>
          <table className="stats-table">
            <tbody>
              <tr><td>{match.bowler}</td><td>{overs(match.bowlerStats.balls)}</td><td>{match.bowlerStats.runs}</td><td>{match.bowlerStats.wickets}</td></tr>
            </tbody>
          </table>
          <div className="subhead recent-head">This innings <span>LAST 12 BALLS</span></div>
          <div className="ball-list">{recent}</div>
          <div className="score-actions" aria-label="Record a delivery">
            <span className="action-label">Runs</span>
            {[0, 1, 2, 3, 4, 5, 6].map(runs => (
              <button
                className="score-action"
                key={runs}
                type="button"
                aria-label={`${runs} runs`}
                disabled={busy}
                onClick={() => onDelivery(match.id, { runs, wicket: false })}
              >{runs}</button>
            ))}
            <button
              className="score-action wicket-button"
              type="button"
              aria-label="Record a wicket"
              disabled={busy}
              onClick={() => onDelivery(match.id, { runs: 0, wicket: true })}
            >W</button>
          </div>
          <p className="selection-note">Choose the runs scored from the bat, or W for a wicket. Entered deliveries are sent to every connected scorecard.</p>
          <MatchLineups match={match} />
        </div>
      ) : (
        <div className="scorecard-content">
          <div className="subhead">{match.status === "UPCOMING" ? "Ready when you are" : "Match result"}</div>
          <p className="match-summary">{match.summary}</p>
          <p className="innings-limit">{match.maxOvers} overs · {match.battingTeam} batting first</p>
          <MatchLineups match={match} />
          {match.status === "UPCOMING" && (
            <button className="create-button start-match-button" type="button" disabled={busy} onClick={() => onStart(match.id)}>Start match</button>
          )}
        </div>
      )}
    </article>
  );
}

function MatchDialog({ open, busy, error, onClose, onCreate }) {
  const dialogRef = useRef(null);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);

  function submit(event) {
    event.preventDefault();
    const form = event.currentTarget;
    if (!form.reportValidity()) return;

    const data = new FormData(form);
    const players = field => String(data.get(field)).split(/[,\n]+/).map(name => name.trim()).filter(Boolean);
    const request = {
      competition: String(data.get("competition")).trim(),
      venue: String(data.get("venue")).trim(),
      homeTeam: String(data.get("homeTeam")).trim(),
      awayTeam: String(data.get("awayTeam")).trim(),
      homePlayers: players("homePlayers"),
      awayPlayers: players("awayPlayers"),
      battingFirst: String(data.get("battingFirst")),
      maxOvers: Number(data.get("maxOvers")),
      startImmediately: data.has("startImmediately")
    };

    if (request.homePlayers.length < 2 || request.homePlayers.length > 11
        || request.awayPlayers.length < 2 || request.awayPlayers.length > 11) {
      onCreate(null, "Enter between 2 and 11 players for each team.");
      return;
    }

    onCreate(request).then(created => {
      if (created) form.reset();
    });
  }

  return (
    <dialog
      className="match-dialog"
      ref={dialogRef}
      aria-labelledby="create-title"
      onCancel={event => {
        event.preventDefault();
        if (!busy) onClose();
      }}
      onClick={event => {
        if (event.target === dialogRef.current && !busy) onClose();
      }}
    >
      <form className="match-form" onSubmit={submit}>
        <div className="dialog-heading">
          <div><p className="section-kicker">NEW SCORECARD</p><h2 id="create-title">Set up your match</h2></div>
          <button className="dialog-close" type="button" aria-label="Close dialog" disabled={busy} onClick={onClose}>×</button>
        </div>
        <p className="dialog-copy">Add the real teams and players. Scores only change when you record a delivery.</p>
        {error && <p className="form-error" role="alert">{error}</p>}
        <div className="form-grid">
          <label className="form-field form-full">Competition or match name<input name="competition" required maxLength="100" placeholder="e.g. Saturday League Final" /></label>
          <label className="form-field form-full">Ground<input name="venue" required maxLength="100" placeholder="e.g. Riverside Cricket Ground" /></label>
          <label className="form-field">Home team<input name="homeTeam" required maxLength="50" placeholder="Team name" /></label>
          <label className="form-field">Away team<input name="awayTeam" required maxLength="50" placeholder="Team name" /></label>
          <label className="form-field">Home players <span>Enter 2–11 names, one per line or comma-separated.</span><textarea name="homePlayers" required rows="4" placeholder={"Opening batter\nSecond batter\nBowler"} /></label>
          <label className="form-field">Away players <span>Enter 2–11 names, one per line or comma-separated.</span><textarea name="awayPlayers" required rows="4" placeholder={"Opening batter\nSecond batter\nBowler"} /></label>
          <label className="form-field">Overs per innings<input name="maxOvers" type="number" min="1" max="1000" defaultValue="20" required /><span>Set any format from 1 to 1,000 overs.</span></label>
          <label className="form-field">Batting first<select name="battingFirst" defaultValue="HOME" required><option value="HOME">Home team</option><option value="AWAY">Away team</option></select></label>
        </div>
        <label className="start-now"><input name="startImmediately" type="checkbox" defaultChecked /><span>Start scoring now</span><small>Leave unchecked to create an upcoming match.</small></label>
        <div className="dialog-actions">
          <button className="cancel-button" type="button" disabled={busy} onClick={onClose}>Cancel</button>
          <button className="create-button" type="submit" disabled={busy}>{busy ? "Creating…" : "Create scorecard"}</button>
        </div>
      </form>
    </dialog>
  );
}

function App() {
  const [matches, setMatches] = useState([]);
  const [loading, setLoading] = useState(true);
  const [selectedId, setSelectedId] = useState(null);
  const [filter, setFilter] = useState("ALL");
  const [search, setSearch] = useState("");
  const [busy, setBusy] = useState(false);
  const [connection, setConnection] = useState("Connecting");
  const [error, setError] = useState("");
  const [dialogOpen, setDialogOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState("");

  const updateMatch = useCallback(match => {
    setMatches(current => {
      const index = current.findIndex(item => item.id === match.id);
      if (index < 0) return [match, ...current];
      if (match.revision < current[index].revision) return current;
      const next = [...current];
      next[index] = match;
      return next;
    });
    setSelectedId(current => current ?? match.id);
  }, []);

  const showServerSnapshot = useCallback(snapshot => {
    setMatches(snapshot);
    setSelectedId(current => snapshot.some(match => match.id === current)
      ? current
      : snapshot.find(match => match.status === "LIVE")?.id ?? snapshot[0]?.id ?? null);
  }, []);

  useEffect(() => {
    let active = true;
    let events;

    async function connect() {
      try {
        const response = await fetch("/api/matches");
        if (!response.ok) throw new Error(`Could not load matches (${response.status})`);
        const snapshot = await response.json();
        if (!active) return;
        showServerSnapshot(snapshot);
        setLoading(false);

        events = new EventSource("/api/matches/stream");
        events.onopen = () => active && setConnection("Live updates");
        events.onerror = () => active && setConnection("Reconnecting");
        events.addEventListener("snapshot", event => {
          try {
            if (active) showServerSnapshot(JSON.parse(event.data));
          } catch {
            if (active) setError("The live update stream returned an unreadable match snapshot.");
          }
        });
        events.addEventListener("score", event => {
          try {
            if (active) updateMatch(JSON.parse(event.data));
          } catch {
            if (active) setError("The live update stream returned an unreadable score.");
          }
        });
      } catch (requestError) {
        if (active) {
          setError(requestError.message);
          setConnection("Unavailable");
          setLoading(false);
        }
      }
    }

    connect();
    return () => {
      active = false;
      events?.close();
    };
  }, [showServerSnapshot, updateMatch]);

  const visibleMatches = useMemo(() => matches.filter(match => {
    const matchesStatus = filter === "ALL" || match.status === filter;
    const searchable = `${match.competition} ${match.venue} ${match.homeTeam.name} ${match.awayTeam.name}`.toLowerCase();
    return matchesStatus && searchable.includes(search.trim().toLowerCase());
  }), [filter, matches, search]);

  const selectedMatch = matches.find(match => match.id === selectedId);

  async function post(url, body) {
    const response = await fetch(url, {
      method: "POST",
      headers: body ? { "Content-Type": "application/json" } : {},
      ...(body ? { body: JSON.stringify(body) } : {})
    });
    if (!response.ok) {
      const result = await response.json().catch(() => ({}));
      throw new Error(result.detail || `Request failed (${response.status})`);
    }
    return response.json();
  }

  async function recordDelivery(id, delivery) {
    if (busy) return;
    setBusy(true);
    setError("");
    try {
      updateMatch(await post(`/api/matches/${encodeURIComponent(id)}/deliveries`, delivery));
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setBusy(false);
    }
  }

  async function startMatch(id) {
    setBusy(true);
    setError("");
    try {
      updateMatch(await post(`/api/matches/${encodeURIComponent(id)}/start`));
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setBusy(false);
    }
  }

  async function createMatch(request, validationError) {
    if (validationError) {
      setCreateError(validationError);
      return false;
    }
    setCreating(true);
    setCreateError("");
    try {
      const created = await post("/api/matches", request);
      setSelectedId(created.id);
      updateMatch(created);
      setDialogOpen(false);
      document.querySelector("#matches").scrollIntoView({ behavior: "smooth", block: "start" });
      return true;
    } catch (requestError) {
      setCreateError(requestError.message);
      return false;
    } finally {
      setCreating(false);
    }
  }

  function openCreateDialog() {
    setCreateError("");
    setDialogOpen(true);
  }

  const count = loading ? "—" : visibleMatches.length === matches.length
    ? String(matches.length).padStart(2, "0")
    : `${visibleMatches.length}/${matches.length}`;

  return (
    <>
      <header className="topbar">
        <a className="brand" href="/" aria-label="Boundary home">
          <span className="brand-mark">B</span><span>boundary<span className="brand-period">.</span></span>
        </a>
        <nav className="main-nav" aria-label="Main navigation">
          <a className="nav-link active" href="#matches">Matches</a>
          <a className="nav-link" href="#scorecard">Scorecard</a>
        </nav>
        <div className="topbar-right">
          <button className="create-button top-create-button" type="button" onClick={openCreateDialog}><span>+</span> Create match</button>
          <span className="connection" role="status" aria-live="polite">
            <i className={connection === "Live updates" ? "connected" : ""} />
            <span>{connection}</span>
          </span>
        </div>
      </header>

      <main>
        <section className="hero page-width" aria-labelledby="page-title">
          <div>
            <p className="eyebrow"><span className="eyebrow-line" /> THE GAME, IN THE MOMENT</p>
            <h1 id="page-title">Every ball.<br /><span>Every moment.</span></h1>
            <p className="hero-copy">Your teams, your players, your scorebook. Create a match and record every delivery as it happens.</p>
            <button className="create-button hero-create-button" type="button" onClick={openCreateDialog}>+ Set up a match</button>
          </div>
          <div className="hero-art" aria-hidden="true">
            <div className="orbit orbit-one" /><div className="orbit orbit-two" /><div className="hero-ball" />
            <span className="art-label">THE<br />BOUNDARY</span>
          </div>
        </section>

        <section id="matches" className="matches-section page-width" aria-labelledby="matches-title">
          <div className="section-heading">
            <div><div className="section-kicker">YOUR SCOREBOOK</div><h2 id="matches-title">Match centre <span className="match-count">{count}</span></h2></div>
            <div className="match-tools">
              <label className="match-search"><span className="visually-hidden">Search matches</span><input type="search" value={search} onChange={event => setSearch(event.target.value)} placeholder="Search your matches" /></label>
              <div className="filters" role="group" aria-label="Filter matches">
                {[["ALL", "All"], ["LIVE", "Live"], ["UPCOMING", "Upcoming"], ["COMPLETED", "Results"]].map(([value, label]) => (
                  <button className={`filter-button ${filter === value ? "selected" : ""}`} key={value} type="button" aria-pressed={filter === value} onClick={() => setFilter(value)}>{label}</button>
                ))}
              </div>
            </div>
          </div>
          {error && <p className="api-error" role="alert">{error}</p>}
          <div className="match-layout">
            <div className="match-list" aria-live="polite">
              {loading ? (
                <div className="loading-card">Loading your scorebook<span className="loading-dots">...</span></div>
              ) : visibleMatches.length ? visibleMatches.map(match => (
                <MatchCard key={match.id} match={match} selected={match.id === selectedId} onSelect={() => setSelectedId(match.id)} />
              )) : matches.length ? (
                <div className="empty-list">No matches match those filters. Try another status or search.</div>
              ) : (
                <div className="empty-list">
                  <strong>Your scorebook is ready.</strong>
                  <span>{error ? "Reconnect to MongoDB and refresh to load saved matches." : "There are no matches yet. Create a scorecard with your teams and lineups to get started."}</span>
                  <button className="create-button" type="button" onClick={openCreateDialog}>+ Create your first match</button>
                </div>
              )}
            </div>
            <div id="scorecard">
              <Scorecard
                match={selectedMatch}
                busy={busy}
                onDelivery={recordDelivery}
                onStart={startMatch}
                onCreate={openCreateDialog}
              />
            </div>
          </div>
        </section>

        <section className="bottom-note page-width">
          <span className="note-icon">✳</span>
          <p><strong>Every score is yours.</strong> Fixtures and scores come from the matches you create and deliveries you record.</p>
          <span className="note-tag">NO SIMULATED SCORES</span>
        </section>
      </main>

      <footer className="footer page-width">
        <a className="brand footer-brand" href="/"><span className="brand-mark">B</span><span>boundary<span className="brand-period">.</span></span></a>
        <span>Made for the love of the game.</span>
        <span className="footer-live"><i /> Your updates stream live</span>
      </footer>

      <MatchDialog
        open={dialogOpen}
        busy={creating}
        error={createError}
        onClose={() => setDialogOpen(false)}
        onCreate={createMatch}
      />
    </>
  );
}

createRoot(document.getElementById("root")).render(<App />);
