import axios from "axios";

export const apiClient = axios.create({
  baseURL: "/api",
  headers: {
    "Content-Type": "application/json"
  }
});

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response) {
      console.error("API error:", error.response);
    } else {
      console.error("Network error:", error);
    }
    return Promise.reject(error);
  }
);
