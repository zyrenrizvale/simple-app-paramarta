let hash = 0;
let seedString = 'PARAMARTHA_SECRET_12345_MASUK';
for (let i = 0; i < seedString.length; i++) {
    hash = (Math.imul(hash, 31) + seedString.charCodeAt(i)) | 0;
}
console.log('JS Hash:', hash);

let randomSeed = (hash >>> 0);
console.log('JS randomSeed 0:', randomSeed);

for (let i = 0; i < 5; i++) {
    randomSeed = (randomSeed * 1103515245 + 12345) % 4294967296;
    console.log('JS randomSeed ' + (i+1) + ':', randomSeed);
}
