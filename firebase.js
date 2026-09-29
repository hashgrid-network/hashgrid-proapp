import { initializeApp } from "firebase/app";
import { getFirestore } from "firebase/firestore";
import { getAuth } from "firebase/auth";

const firebaseConfig = {
  apiKey: "AIzaSyD8XqcWgN9EbaUSBVYhf5oBk1qgo3lHnyA",
  authDomain: "hashgrid-b850b.firebaseapp.com",
  databaseURL: "https://hashgrid-b850b-default-rtdb.asia-southeast1.firebasedatabase.app",
  projectId: "hashgrid-b850b",
  storageBucket: "hashgrid-b850b.firebasestorage.app",
  messagingSenderId: "621346408367",
  appId: "1:621346408367:web:7621be60d110b9105a915a",
  measurementId: "G-4V12ST99J8"
};

const app = initializeApp(firebaseConfig);
export const db = getFirestore(app);
export const auth = getAuth(app);
