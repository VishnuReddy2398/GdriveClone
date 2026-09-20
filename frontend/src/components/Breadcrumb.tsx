import React from 'react';
import { useNavigate } from 'react-router-dom';
import { ChevronRight } from 'lucide-react';
import styles from './Breadcrumb.module.css';

interface BreadcrumbItem {
  id: string | null;
  name: string;
}

interface BreadcrumbProps {
  path: BreadcrumbItem[];
}

const Breadcrumb: React.FC<BreadcrumbProps> = ({ path }) => {
  const navigate = useNavigate();

  const handleClick = (item: BreadcrumbItem) => {
    if (item.id === null) {
      navigate('/drive/my-drive');
    } else {
      navigate(`/drive/folders/${item.id}`);
    }
  };

  return (
    <nav className={styles.breadcrumb}>
      {path.map((item, index) => (
        <React.Fragment key={item.id ?? 'root'}>
          {index > 0 && <ChevronRight size={16} className={styles.separator} />}
          {index < path.length - 1 ? (
            <span className={styles.breadcrumbLink} onClick={() => handleClick(item)}>
              {item.name}
            </span>
          ) : (
            <span className={styles.breadcrumbCurrent}>{item.name}</span>
          )}
        </React.Fragment>
      ))}
    </nav>
  );
};

export default Breadcrumb;
