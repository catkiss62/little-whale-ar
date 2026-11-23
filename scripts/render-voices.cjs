// Offline render using smmcat/animalese-api (Coopanion, AGPL-3.0-or-later).
// Keep its actual syllable timestamps, including punctuation gaps, with each line.
const fs=require('node:fs'),path=require('node:path'),Module=require('node:module');
const lines=require('../speech/lines.json');
const file=path.resolve(process.argv[2],'src/speechEngine.js');
const output=process.argv[3];
const engine=new Module(file,module);
engine.filename=file;engine.paths=Module._nodeModulePaths(path.dirname(file));
const placement='placements.push({ samples: fragment, atSec: t });';
const source=fs.readFileSync(file,'utf8');
if(!source.includes(placement))throw Error('Unknown speech engine revision');
engine._compile(source.replace(placement,placement+' module.exports.mark(i,t);'),file);
engine.exports.preloadAssets();
fs.mkdirSync(output,{recursive:true});
const groups={};
for(const [group,texts] of Object.entries(lines)){
    groups[group]=texts.map((text,i)=>{
        const cues=[];
        engine.exports.mark=(index,t)=>cues.push([index,Math.round(t*1000)]);
        const {buffer}=engine.exports.render(text);
        const id=group+i;
        fs.writeFileSync(path.join(output,id+'.wav'),buffer);
        return {id,text,cues};
    });
}
fs.writeFileSync(path.join(output,'lines.json'),JSON.stringify(groups));
fs.writeFileSync(path.join(output,'SOURCE.txt'),'Animalese voices rendered offline with https://github.com/smmcat/animalese-api at 014ec5c, based on Pal-AI-Lab/Coopanion (AGPL-3.0-or-later). See scripts/render-voices.cjs and speech/lines.json in the Little Whale AR repository.');
