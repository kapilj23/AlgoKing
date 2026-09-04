import { writeFileSync } from "node:fs";

const OUT = "C:/Users/kapil/AndroidStudioProjects/AlgoKing/docs/brand";

// ---------------------------------------------------------------- geometry
// One monogram, drawn in its own coordinate space (x 255..857, y 355..660) and
// then placed by a single transform. Every variant uses the SAME paths, so the
// three icons are provably the same mark at three densities.
const A_PATH =
  "M384,355 L416,355 L545,660 L255,660 Z " +      // outer wedge
  "M400,457 L434,528 L366,528 Z " +               // counter
  "M341,580 L459,580 L497,660 L303,660 Z";        // leg gap (crossbar = 528..580)

const K_PATH =
  "M585,355 L657,355 L657,660 L585,660 Z " +      // stem
  "M740,355 L835,355 L752,500 L657,500 Z " +      // arm
  "M657,500 L752,500 L857,660 L762,660 Z";        // leg

const MONO_CX = 556, MONO_CY = 507.5;             // bbox centre of A+K

// The crown is sized off the monogram, not invented: its outer finials land at
// ~73% of the monogram width, so it reads as a crown ON the mark rather than a
// hat on the A. Peaks terminate in graph nodes; the apex node is the blue jewel.
const CROWN = "M316,315 L316,214 L428,282 L512,160 L596,282 L708,214 L708,315 Z";
export const crown = (p) => `
      <path d="${CROWN}" fill="#E7AE33" opacity="0.24" filter="url(#${p}-soft)"/>
      <path d="${CROWN}" fill="url(#${p}-gold)"/>
      <path d="M512,160 L512,282 L428,282 Z" fill="#FFF4CE" opacity="0.26"/>
      <path d="M512,160 L512,282 L596,282 Z" fill="#7A4E0E" opacity="0.20"/>
      <path d="M316,214 L316,315 L375,288 Z" fill="#FFF4CE" opacity="0.18"/>
      <path d="M708,214 L708,315 L649,288 Z" fill="#7A4E0E" opacity="0.16"/>
      <rect x="307" y="315" width="410" height="47" rx="16" fill="url(#${p}-goldBand)"/>
      <rect x="316" y="320" width="392" height="9" rx="4.5" fill="#FFF7DC" opacity="0.28"/>
      <rect x="316" y="349" width="392" height="9" rx="4.5" fill="#6B430B" opacity="0.26"/>
      <circle cx="316" cy="214" r="15" fill="url(#${p}-gold)"/>
      <circle cx="708" cy="214" r="15" fill="url(#${p}-gold)"/>
      <circle cx="512" cy="152" r="23" fill="url(#${p}-gold)"/>
      <circle cx="512" cy="152" r="17" fill="url(#${p}-blue)"/>
      <circle cx="506" cy="146" r="5" fill="#EAF6FF" opacity="0.85"/>`;

export const monogram = (p, scale, cy) => `
    <g filter="url(#${p}-drop)" transform="translate(512 ${cy}) scale(${scale}) translate(${-MONO_CX} ${-MONO_CY})">
      <path fill-rule="evenodd" fill="url(#${p}-silver)" d="${A_PATH}"/>
      <g clip-path="url(#${p}-clipA)"><rect x="245" y="345" width="310" height="325" fill="url(#${p}-sheenLetter)"/></g>
      <path fill-rule="evenodd" fill="none" stroke="#FFFFFF" stroke-opacity="0.32" stroke-width="2" d="${A_PATH}"/>
      <path fill="url(#${p}-gold)" d="${K_PATH}"/>
      <g clip-path="url(#${p}-clipK)"><rect x="575" y="345" width="292" height="325" fill="url(#${p}-sheenLetter)"/></g>
      <path fill="none" stroke="#FFF4CE" stroke-opacity="0.34" stroke-width="2" d="${K_PATH}"/>
    </g>`;

// ---------------------------------------------------------------- shared defs
export const defs = (p, { grid = false } = {}) => `
    <clipPath id="${p}-tile"><rect width="1024" height="1024" rx="228" ry="228"/></clipPath>
    <linearGradient id="${p}-ground" x1="0.1" y1="0" x2="0.7" y2="1">
      <stop offset="0" stop-color="#141F42"/><stop offset="0.5" stop-color="#0A1128"/><stop offset="1" stop-color="#04070D"/>
    </linearGradient>
    <radialGradient id="${p}-lift" cx="0.5" cy="0.32" r="0.75">
      <stop offset="0" stop-color="#22376F" stop-opacity="0.70"/><stop offset="0.5" stop-color="#101B3C" stop-opacity="0.30"/><stop offset="1" stop-color="#04070D" stop-opacity="0"/>
    </radialGradient>
    <radialGradient id="${p}-bounce" cx="0.5" cy="0.5" r="0.5">
      <stop offset="0" stop-color="#F0BB44" stop-opacity="0.26"/><stop offset="1" stop-color="#F0BB44" stop-opacity="0"/>
    </radialGradient>
    <radialGradient id="${p}-scrim" cx="0.5" cy="0.5" r="0.5">
      <stop offset="0" stop-color="#03060C" stop-opacity="0.62"/><stop offset="0.62" stop-color="#03060C" stop-opacity="0.34"/><stop offset="1" stop-color="#03060C" stop-opacity="0"/>
    </radialGradient>
    <linearGradient id="${p}-sheen" x1="0" y1="0" x2="0.55" y2="1">
      <stop offset="0" stop-color="#CFE2FF" stop-opacity="0.10"/><stop offset="0.4" stop-color="#CFE2FF" stop-opacity="0.015"/><stop offset="1" stop-color="#000308" stop-opacity="0.22"/>
    </linearGradient>
    <linearGradient id="${p}-rim" x1="0" y1="0" x2="0.6" y2="1">
      <stop offset="0" stop-color="#FFFFFF" stop-opacity="0.24"/><stop offset="0.45" stop-color="#FFFFFF" stop-opacity="0.04"/><stop offset="1" stop-color="#F2C868" stop-opacity="0.16"/>
    </linearGradient>
    <linearGradient id="${p}-gold" x1="0.12" y1="0" x2="0.85" y2="1">
      <stop offset="0" stop-color="#FFF4CE"/><stop offset="0.18" stop-color="#F9D882"/><stop offset="0.42" stop-color="#E7AE33"/>
      <stop offset="0.68" stop-color="#BE801B"/><stop offset="0.86" stop-color="#E0A62C"/><stop offset="1" stop-color="#FFEBB4"/>
    </linearGradient>
    <linearGradient id="${p}-goldBand" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#FFEDB6"/><stop offset="0.45" stop-color="#E7AE33"/><stop offset="1" stop-color="#A9701A"/>
    </linearGradient>
    <linearGradient id="${p}-silver" x1="0.12" y1="0" x2="0.8" y2="1">
      <stop offset="0" stop-color="#FFFFFF"/><stop offset="0.22" stop-color="#F1F5FF"/><stop offset="0.5" stop-color="#C6D2E8"/>
      <stop offset="0.74" stop-color="#909FBE"/><stop offset="0.9" stop-color="#D5DEEF"/><stop offset="1" stop-color="#FFFFFF"/>
    </linearGradient>
    <linearGradient id="${p}-blue" x1="0" y1="0" x2="0.4" y2="1">
      <stop offset="0" stop-color="#8CCBFF"/><stop offset="0.45" stop-color="#3D8BFF"/><stop offset="1" stop-color="#1444B8"/>
    </linearGradient>
    <linearGradient id="${p}-floor" x1="0" y1="0" x2="1" y2="0">
      <stop offset="0" stop-color="#9DC4FF" stop-opacity="0"/><stop offset="0.5" stop-color="#9DC4FF" stop-opacity="0.32"/><stop offset="1" stop-color="#9DC4FF" stop-opacity="0"/>
    </linearGradient>
    <linearGradient id="${p}-sheenLetter" x1="0" y1="0" x2="0.5" y2="1">
      <stop offset="0" stop-color="#FFFFFF" stop-opacity="0.46"/><stop offset="0.34" stop-color="#FFFFFF" stop-opacity="0.05"/>
      <stop offset="0.63" stop-color="#000814" stop-opacity="0.16"/><stop offset="1" stop-color="#FFFFFF" stop-opacity="0.20"/>
    </linearGradient>${grid ? `
    <pattern id="${p}-grid" width="64" height="64" patternUnits="userSpaceOnUse">
      <path d="M64 0H0V64" fill="none" stroke="#9DC4FF" stroke-width="1" stroke-opacity="0.09"/>
    </pattern>
    <radialGradient id="${p}-gridFade" cx="0.5" cy="0.44" r="0.63">
      <stop offset="0" stop-color="#FFFFFF" stop-opacity="0.85"/><stop offset="1" stop-color="#FFFFFF" stop-opacity="0"/>
    </radialGradient>
    <mask id="${p}-gridMask"><rect width="1024" height="1024" fill="url(#${p}-gridFade)"/></mask>` : ""}
    <filter id="${p}-soft" x="-60%" y="-60%" width="220%" height="220%"><feGaussianBlur stdDeviation="17"/></filter>
    <filter id="${p}-glow" x="-120%" y="-120%" width="340%" height="340%"><feGaussianBlur stdDeviation="10"/></filter>
    <filter id="${p}-drop" x="-30%" y="-30%" width="170%" height="170%">
      <feDropShadow dx="0" dy="11" stdDeviation="15" flood-color="#000610" flood-opacity="0.58"/>
    </filter>
    <filter id="${p}-dropSoft" x="-40%" y="-40%" width="180%" height="180%">
      <feDropShadow dx="0" dy="6" stdDeviation="9" flood-color="#000610" flood-opacity="0.45"/>
    </filter>
    <clipPath id="${p}-clipA" clipPathUnits="userSpaceOnUse"><path clip-rule="evenodd" d="${A_PATH}"/></clipPath>
    <clipPath id="${p}-clipK" clipPathUnits="userSpaceOnUse"><path d="${K_PATH}"/></clipPath>`;

const shell = (p, title, { grid = false }, body) => `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024" width="1024" height="1024">
  <title>${title}</title>
  <defs>${defs(p, { grid })}
  </defs>
  <g clip-path="url(#${p}-tile)">
    <rect width="1024" height="1024" fill="url(#${p}-ground)"/>
    <rect width="1024" height="1024" fill="url(#${p}-lift)"/>${grid ? `
    <rect width="1024" height="1024" fill="url(#${p}-grid)" mask="url(#${p}-gridMask)"/>` : ""}
    <ellipse cx="512" cy="872" rx="345" ry="122" fill="url(#${p}-bounce)"/>
${body}
    <rect width="1024" height="1024" fill="url(#${p}-sheen)"/>
    <rect x="1.5" y="1.5" width="1021" height="1021" rx="226.5" fill="none" stroke="url(#${p}-rim)" stroke-width="3"/>
  </g>
</svg>
`;

// ---------------------------------------------------------------- variants
// 1 — SOVEREIGN: crown + AK + a four-step climb, the last step in gold.
// Bars are rounded on top only and share one baseline, so the row reads as a
// chart standing on a floor rather than five floating pills.
const bar = (x, y, w, base, r, fill, extra = "") =>
  `      <path d="M${x},${base} L${x},${y + r} Q${x},${y} ${x + r},${y} L${x + w - r},${y} Q${x + w},${y} ${x + w},${y + r} L${x + w},${base} Z" fill="${fill}"${extra}/>`;

export const bars = (p) => {
  // Four bars, not five: at 48dp a 62px bar is under three pixels wide and the
  // row turns into a smudge. Fewer, wider bars survive the smallest size.
  const xs = [327, 425, 523, 621], h = [24, 40, 58, 78], w = 76, base = 818;
  const rows = xs.map((x, i) => {
    const gold = i === xs.length - 1;
    return bar(x, base - h[i], w, base, 12,
      `url(#${p}-${gold ? "gold" : "blue"})`,
      gold ? "" : ` opacity="${(0.74 + i * 0.08).toFixed(2)}"`);
  }).join("\n");
  // The glow sits outside the shadowed group; inside it, the blur and the drop
  // shadow compound into a muddy blob under the row.
  return `    <ellipse cx="659" cy="790" rx="78" ry="58" fill="#F0BB44" opacity="0.11" filter="url(#${p}-soft)"/>
    <g filter="url(#${p}-dropSoft)">
${rows}
    </g>
    <rect x="300" y="818" width="424" height="3" rx="1.5" fill="url(#${p}-floor)"/>`;
};

writeFileSync(`${OUT}/algoking-icon-01-sovereign.svg`, shell("s",
  "AlgoKing app icon - Sovereign (primary)", { grid: true },
  `    <ellipse cx="512" cy="520" rx="405" ry="335" fill="url(#s-scrim)"/>
    <g filter="url(#s-drop)" transform="translate(0 71)">${crown("s")}
    </g>
${monogram("s", 0.83, 571)}
${bars("s")}`));

// 2 — SIGNET: the mark alone, set as large as the tile allows.
writeFileSync(`${OUT}/algoking-icon-02-signet.svg`, shell("m",
  "AlgoKing app icon - Signet (minimal)", { grid: false },
  `    <circle cx="512" cy="512" r="396" fill="none" stroke="#F2C868" stroke-opacity="0.10" stroke-width="2"/>
    <ellipse cx="512" cy="530" rx="410" ry="340" fill="url(#m-scrim)"/>
    <g filter="url(#m-drop)" transform="translate(0 120)">${crown("m")}
    </g>
${monogram("m", 0.92, 634)}`));

// 3 — TRAVERSAL: the mark standing in a graph, with one path solved in gold.
// A binary tree in the slot Sovereign gives to the bar chart — below the mark,
// never around it. A graph drawn around the monogram closes into a wreath no
// matter how carefully the two halves are kept apart.
// One root-to-leaf path is solved in gold: the search that found its answer.
const tree = (p) => {
  const R = [512, 686], L1 = [[404, 772], [620, 772]];
  const L2 = [[348, 858], [460, 858], [564, 858], [676, 858]];
  const edge = (a, b, gold) =>
    `      <path d="M${a[0]},${a[1]} L${b[0]},${b[1]}" stroke="${gold ? `url(#${p}-gold)` : "#4D9BFF"}" stroke-opacity="${gold ? 0.95 : 0.34}" stroke-width="${gold ? 5 : 4}" stroke-linecap="round"/>`;
  const dot = (c, r, gold) => gold
    ? `      <circle cx="${c[0]}" cy="${c[1]}" r="${r}" fill="url(#${p}-gold)"/>`
    : `      <circle cx="${c[0]}" cy="${c[1]}" r="${r}" fill="#0A1128" stroke="#5FA8FF" stroke-opacity="0.42" stroke-width="4"/>`;
  return `    <g fill="none">
${edge(R, L1[0], false)}
${edge(R, L1[1], true)}
${edge(L1[0], L2[0], false)}
${edge(L1[0], L2[1], false)}
${edge(L1[1], L2[2], true)}
${edge(L1[1], L2[3], false)}
    </g>
    <g>
${dot(R, 19, true)}
${dot(L1[0], 17, false)}
${dot(L1[1], 17, true)}
${dot(L2[0], 14, false)}
${dot(L2[1], 14, false)}
${dot(L2[2], 15, true)}
${dot(L2[3], 14, false)}
      <circle cx="564" cy="858" r="26" fill="none" stroke="#F0BB44" stroke-opacity="0.28" stroke-width="3"/>
    </g>`;
};

writeFileSync(`${OUT}/algoking-icon-03-traversal.svg`, shell("t",
  "AlgoKing app icon - Traversal (algorithm-forward)", { grid: true },
  `    <ellipse cx="512" cy="500" rx="410" ry="330" fill="url(#t-scrim)"/>
    <g filter="url(#t-drop)" transform="translate(0 29)">${crown("t")}
    </g>
${monogram("t", 0.80, 525)}
    <g filter="url(#t-dropSoft)">
${tree("t")}
    </g>`));

console.log("built 3 icons");
