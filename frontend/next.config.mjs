const nextConfig = {
  allowedDevOrigins: ["127.0.0.1"],
  async rewrites() {
    return [
      {
        source: "/index.html",
        destination: "/"
      },
      {
        source: "/simplified-data",
        destination: "/import-data"
      },
      {
        source: "/simplified-data.html",
        destination: "/import-data"
      },
      {
        source: "/evidence",
        destination: "/evidence-docs"
      },
      {
        source: "/evidence.html",
        destination: "/evidence-docs"
      },
      {
        source: "/:slug.html",
        destination: "/:slug"
      },
      {
        source: "/api/:path*",
        destination: "http://127.0.0.1:8080/api/:path*"
      }
    ];
  }
};

export default nextConfig;
