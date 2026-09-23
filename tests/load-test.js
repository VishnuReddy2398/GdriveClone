import http from 'k6/http';

export const options = {
    stages: [
        { duration: '30s', target: 50 },  // Ramp up to 50 users
        { duration: '1m', target: 200 },  // Ramp up to 200 users
        { duration: '30s', target: 0 },   // Ramp down to 0 users
    ],
};

export default function () {
    http.get('http://localhost:8080/api/health'); // Hitting your API!
}
