import { BrowserRouter, Routes, Route } from 'react-router-dom';
import Home from './pages/Home';
import Learn from './pages/Learn';
import Practice from './pages/Practice';
function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path='/' element={<Home />} />
        <Route path='/learn' element={<Learn />} />
        <Route path='/practice' element={<Practice />} />
      </Routes>
    </BrowserRouter>
  );
}
export default App;
