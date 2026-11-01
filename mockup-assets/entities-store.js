(function(){
const key='know-entity-mock-v1';
const seed=[
{id:'book-design',type:'books',title:'The Design of Everyday Things',status:'Read',created:'2026-01-12',finished:'2026-03-20',sourceEntryId:'design-book',sourceTitle:'The Design of Everyday Things'},
{id:'book-dune',type:'books',title:'Dune',status:'Reading',created:'2026-10-04',finished:''},
{id:'book-atomic',type:'books',title:'Atomic Habits',status:'To read',created:'2026-01-10',finished:''},
{id:'book-1984',type:'books',title:'1984',status:'Read',created:'2025-05-03',finished:'2025-06-18'},
{id:'movie-arrival',type:'movies',title:'Arrival',status:'Seen',created:'2026-02-08',finished:'2026-02-15',sourceEntryId:'arrival',sourceTitle:'Arrival'},
{id:'movie-interstellar',type:'movies',title:'Interstellar',status:'To be seen',created:'2025-08-21',finished:''},
{id:'movie-martian',type:'movies',title:'The Martian',status:'To be seen',created:'2026-01-05',finished:''},
{id:'movie-moon',type:'movies',title:'Moon',status:'Seen',created:'2025-03-11',finished:'2025-04-08'},
{id:'tv-severance',type:'tv',title:'Severance',status:'Seen',created:'2026-04-03',finished:'2026-06-18',sourceEntryId:'severance',sourceTitle:'Severance'},
{id:'tv-bear',type:'tv',title:'The Bear',status:'Seeing',created:'2026-01-11',finished:''},
{id:'tv-dark',type:'tv',title:'Dark',status:'To be seen',created:'2025-02-14',finished:'2025-03-10'},
{id:'game-hades',type:'games',title:'Hades',status:'Playing',created:'2026-09-10',finished:'',sourceEntryId:'hades',sourceTitle:'Hades: a journey through the underworld'},
{id:'game-portal',type:'games',title:'Portal 2',status:'Finished',created:'2026-04-04',finished:'2026-04-20'},
{id:'game-celeste',type:'games',title:'Celeste',status:'To play',created:'2026-10-10',finished:''},
{id:'game-journey',type:'games',title:'Journey',status:'Finished',created:'2025-06-01',finished:'2025-07-05'}
];
const types={books:{label:'Books',singular:'book',statuses:['To read','Reading','Read'],done:'Read',year:'Read year',finished:'Read on'},movies:{label:'Movies',singular:'movie',statuses:['To be seen','Seen'],done:'Seen',year:'Seen year',finished:'Seen on'},tv:{label:'TV shows',singular:'TV show',statuses:['To be seen','Seeing','Seen'],done:'Seen',year:'Seen year',finished:'Seen on'},games:{label:'Games',singular:'game',statuses:['To play','Playing','Finished'],done:'Finished',year:'Finished year',finished:'Finished on'}};
const valid=rows=>Array.isArray(rows)&&rows.every(e=>e&&typeof e.id==='string'&&types[e.type]&&typeof e.title==='string'&&types[e.type].statuses.includes(e.status)&&typeof e.created==='string'&&typeof e.finished==='string');
let rows=seed.map(e=>({...e}));
try{const saved=JSON.parse(localStorage.getItem(key));if(valid(saved))rows=saved;}catch{}
try{const shared=JSON.parse(new URLSearchParams(location.hash.slice(1)).get('mock-state'));if(valid(shared))rows=shared;}catch{}
function refreshLinks(){document.querySelectorAll('a[href]').forEach(a=>{const target=a.getAttribute('href').split('#')[0];if(['web-mockups.html','entities-mockups.html'].includes(target))a.dataset.mockLink=target;});document.querySelectorAll('[data-mock-link]').forEach(a=>{const params=new URLSearchParams();params.set('mock-state',JSON.stringify(rows));if(a.dataset.mockType)params.set('type',a.dataset.mockType);params.set('preview',document.body.dataset.previewPlatform==='mobile'?'mobile':'desktop');if(a.dataset.sourceEntry)params.set('entry',a.dataset.sourceEntry);a.setAttribute('href',a.dataset.mockLink+'#'+params.toString());});}
function save(){try{localStorage.setItem(key,JSON.stringify(rows));}catch{}refreshLinks();window.dispatchEvent(new Event('know-entities-change'));}
function restorePreview(){const mode=new URLSearchParams(location.hash.slice(1)).get('preview');if(mode==='mobile'||mode==='desktop')document.body.dataset.previewPlatform=mode;document.querySelectorAll('[data-web-view]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.webView===document.body.dataset.previewPlatform)));}
window.KnowMockEntities={types,restorePreview,list:()=>rows.map(e=>({...e})),save,refreshLinks,add(entity){const row={...entity,id:'manual-'+Date.now()+'-'+Math.random().toString(36).slice(2)};rows.push(row);save();return row.id;},update(id,changes){const row=rows.find(e=>e.id===id);if(row)Object.assign(row,changes);save();},findSource:id=>rows.find(e=>e.sourceEntryId===id),addSource(entry){if(this.findSource(entry.id))return;return this.add({type:entry.category,title:entry.entityName,status:types[entry.category].statuses[0],created:new Date().toLocaleDateString('en-CA'),finished:'',sourceEntryId:entry.id,sourceTitle:entry.title});},removeSource(id){rows=rows.filter(e=>e.sourceEntryId!==id);save();}};
document.addEventListener('DOMContentLoaded',()=>{restorePreview();refreshLinks();document.querySelectorAll('[data-web-view]').forEach(b=>b.addEventListener('click',refreshLinks));});
})();
