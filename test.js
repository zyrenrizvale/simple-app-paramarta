let hash = 0;
let seedString = 'PARAMARTHA_SECRET_12345_MASUK';
for (let i = 0; i < seedString.length; i++) {
    hash = (Math.imul(hash, 31) + seedString.charCodeAt(i)) | 0;
}
const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
let result = '';
let randomSeed = (hash >>> 0);
for (let i = 0; i < 5; i++) {
    randomSeed = (randomSeed * 1103515245 + 12345) % 4294967296;
    result += chars[Math.floor(randomSeed % chars.length)];
}
console.log('JS Token:', result);
