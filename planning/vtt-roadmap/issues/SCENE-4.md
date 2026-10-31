**Roadmap ID:** `SCENE-4` · **Priority:** P1 · **Relative size:** L

Proposed roadmap work based on source inspection at `ee0f937c73`; this is not a confirmed defect report. Priorities and sizes are planning recommendations.

## Describe the problem

Region actions need explicit movement semantics, trusted execution, and duplicate suppression across multiplayer clients. Existing general movement callbacks are useful building blocks but do not establish that contract. No defect is asserted.

## Proposed scope

Implement region events over committed token movement, beginning with token-center geometry and GM-authorized library handlers. Break implementation into smaller PRs while retaining one agreed event contract.

## Acceptance criteria

- [ ] Document enter, exit, and crossing semantics, including boundaries, waypoints, canceled movement, and programmatic relocation.
- [ ] A single designated session authority dispatches each committed event once; event identifiers prevent duplicate processing.
- [ ] GM can bind and disable trusted handlers; players cannot install privileged handlers through region data.
- [ ] Errors are reported without blocking later movement; recursion and chained relocation are bounded.
- [ ] Tests cover crossing without stopping inside, multi-token movement, retries and reconnects, canceled moves, and handler failures.

## Parent epic

{{SCENE-EPIC}}

## Dependencies

- {{SCENE-3}}
- {{ACCESS-6}}

## Source evidence

- [src/main/java/net/rptools/maptool/client/functions/TokenMoveFunctions.java:63](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/functions/TokenMoveFunctions.java#L63): Movement callbacks and path helpers already provide integration points.
- [src/main/java/net/rptools/maptool/client/functions/TokenMoveFunctions.java:439](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/client/functions/TokenMoveFunctions.java#L439): The legacy movement veto path intentionally invokes only the first matching handler; new region semantics should be explicit and preserve compatibility.
- [src/main/java/net/rptools/maptool/util/EventMacroUtil.java:34](https://github.com/Nav1212/maptool/blob/ee0f937c736b6dfe7c14ad2a950e818b05cf3b76/src/main/java/net/rptools/maptool/util/EventMacroUtil.java#L34): Existing utility executes trusted library event handlers.

## Validation

Verify the acceptance criteria with representative existing campaigns and focused checks appropriate to the change. Multiplayer or persistence changes must cover remote updates, reconnects, and save/reload as applicable. Discovery items must record tested assumptions and resulting decisions.
