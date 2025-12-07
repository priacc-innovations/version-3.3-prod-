// src/api/axiosInstance.ts
import axios, { InternalAxiosRequestConfig } from "axios";

const api = axios.create({
  baseURL: "/api",
});

api.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    if (typeof window !== "undefined") {
      const token = localStorage.getItem("token");  // FIXED
      if (token) {
        config.headers.set("Authorization", `Bearer ${token}`);
      }
    }
    return config;
  },
  (error) => Promise.reject(error)    
);

export default api;
