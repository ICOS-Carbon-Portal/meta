const path = require('path');
const { defineConfig } = require('vite');

const scalaClassDir = process.env.SCALA_CLASS_DIR;
if (!scalaClassDir) {
	console.error("SCALA_CLASS_DIR environment variable is not set. Use classDirectory setting from SBT to get the value to set it to.");
	process.exit(1);
}

module.exports = defineConfig(({ mode }) => ({
	build: {
		outDir: path.resolve(scalaClassDir, 'www'),
		emptyOutDir: false,   // gulp writes its bundles to the same directory
		sourcemap: false,     // StaticRoute serves only www/station.js
		minify: mode === 'production',
		rollupOptions: {
			input: { station: path.resolve(__dirname, 'main.js') },
			output: { entryFileNames: '[name].js' }
		}
	}
}));
