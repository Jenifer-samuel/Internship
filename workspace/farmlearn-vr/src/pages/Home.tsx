import { Link } from 'react-router-dom';
export default function Home() {
  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50 p-6">
      <div className="max-w-md w-full bg-white rounded-xl shadow p-8 text-center space-y-6">
        <h1 className="text-2xl font-bold">FarmLearn VR</h1>
        <p className="text-gray-600">Choose a module to begin</p>
        <div className="grid grid-cols-1 gap-4">
          <Link to="/learn" className="w-full inline-flex items-center justify-center rounded-lg bg-blue-600 px-4 py-3 text-white font-medium hover:bg-blue-700">Learn</Link>
          <Link to="/practice" className="w-full inline-flex items-center justify-center rounded-lg bg-green-600 px-4 py-3 text-white font-medium hover:bg-green-700">Practice</Link>
        </div>
      </div>
    </div>
  );
}
